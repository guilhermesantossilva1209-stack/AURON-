package br.com.auron.domain

interface AiProvider {
    val id: String
    val isDemo: Boolean
    suspend fun reply(input: String, settings: AppSettings): ProviderResult
    suspend fun reply(request: AiRequest): ProviderResult = reply(request.input, request.settings)
}

data class AiRequest(
    val input: String,
    val settings: AppSettings,
    val history: List<ChatMessage> = emptyList(),
    val memories: List<MemoryRecord> = emptyList(),
)

sealed interface ProviderResult {
    data class Success(val text: String) : ProviderResult
    data class Unavailable(val reason: String) : ProviderResult
}

/** Offline, deterministic demonstration. There are no model calls or hidden tools. */
class DemoAiProvider : AiProvider {
    override val id = "local-demo-v1"
    override val isDemo = true

    override suspend fun reply(input: String, settings: AppSettings): ProviderResult {
        if (input.isBlank()) return ProviderResult.Unavailable("Escreva uma mensagem para continuar.")
        if (input.length > InputLimits.MESSAGE) {
            return ProviderResult.Unavailable("A mensagem deve ter até ${InputLimits.MESSAGE} caracteres.")
        }
        val normalized = CommandParser.normalize(input)
        val words = normalized.split(Regex("[^a-z0-9]+")).toSet()
        val name = settings.userName.trim().take(InputLimits.USER_NAME).ifBlank { "comandante" }
        val answer = when {
            words.any { it in setOf("suicidio", "suicidar", "automutilacao") } ||
                listOf("me matar", "me machucar", "tirar minha vida", "nao quero viver").any(normalized::contains) ->
                "Se você corre risco de se ferir agora, procure alguém de confiança e atendimento imediato: " +
                    "SAMU 192 ou emergência local. O CVV atende pelo 188 para apoio emocional. " +
                    "Este modo demonstrativo não oferece acompanhamento clínico."
            words.any { it in setOf("medicacao", "remedio", "dose", "medico", "saude", "prescricao") } ->
                "Não avalio sintomas nem ajusto medicação. Siga sua prescrição e procure um " +
                    "profissional de saúde para dúvidas. Você pode organizar uma tarefa para essa consulta."
            words.any { it in setOf("investir", "investimento", "financeiro", "lucro", "payout", "aposta", "banca", "dinheiro") } ->
                "O módulo financeiro está planejado. Neste modo não analiso investimentos, executo " +
                    "operações nem prometo retornos. Você pode registrar uma tarefa para revisar suas contas."
            words.any { it in setOf("triste", "ansioso", "ansiedade", "depressao", "emocional") } ->
                "Você pode fazer uma pausa e procurar alguém de confiança. Este modo local tem " +
                    "respostas fixas e não substitui acompanhamento profissional. O diário emocional ainda está planejado."
            CommandParser.parse(input) == LocalCommand.Help ->
                "Use “criar tarefa: Revisar roteiro”, “concluir tarefa: Revisar roteiro”, " +
                    "“concluir atividade: Beber água”, “mostre minha rotina”, “o que falta fazer?”, " +
                    "“bom dia, AURON”, “boa noite, AURON” ou “abrir configurações”. " +
                    "Para concluir algo, informe o título exato; não escolho um item por você."
            else -> {
                val greeting = if (settings.humor > 50) "$name, central local a postos. " else ""
                greeting + "Ainda não há um modelo de IA conectado. Tenho respostas fixas e comandos " +
                    "locais para tarefas, rotina e navegação. Digite “ajuda” para ver exemplos."
            }
        }
        return ProviderResult.Success("Demonstração local • $answer")
    }
}
