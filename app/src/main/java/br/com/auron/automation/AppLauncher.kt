package br.com.auron.automation

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

/**
 * Opens only visible, exported launcher activities. Call from an explicit user
 * action while AURON is in the foreground; never from notification content.
 * Package visibility is limited by the MAIN/LAUNCHER query in the manifest.
 */
class AppLauncher(context: Context) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    fun listApps(): List<LaunchableApp> = launcherActivities()
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            val packageName = info.activityInfo.packageName
            val label = runCatching { info.loadLabel(packageManager)?.toString() }
                .getOrNull().orEmpty().filterNot(Char::isISOControl).trim().take(160)
            LaunchableApp(packageName, label.ifBlank { packageName })
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /** Null means Android accepted the launch. A message explains failures. */
    fun open(app: LaunchableApp): String? {
        if (app.packageName == context.packageName || app.packageName.isBlank()) {
            return "Escolha um aplicativo disponível na lista."
        }
        val info = launcherActivities(app.packageName).firstOrNull()
            ?: return "Esse aplicativo não está disponível para abertura. Ele pode ter sido removido, desativado ou bloqueado pelo Android."
        val intent = launcherIntent().setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            null
        } catch (_: ActivityNotFoundException) {
            "O aplicativo não está mais disponível. Atualize a lista e tente novamente."
        } catch (_: SecurityException) {
            "O Android não autorizou abrir esse aplicativo. Confira suas restrições e permissões."
        } catch (_: RuntimeException) {
            "Não foi possível abrir o aplicativo neste momento."
        }
    }

    @Suppress("DEPRECATION") // This overload supports the project's Android 8.0 minimum.
    private fun launcherActivities(packageName: String? = null): List<ResolveInfo> = try {
        packageManager.queryIntentActivities(launcherIntent().apply { packageName?.let(::setPackage) }, 0)
            .filter { result ->
                val activity = result.activityInfo
                activity != null && activity.exported && activity.enabled && activity.applicationInfo.enabled &&
                    (packageName == null || activity.packageName == packageName)
            }
            .sortedBy { it.activityInfo.name }
    } catch (_: RuntimeException) {
        emptyList()
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
