import { createHash, randomBytes } from 'node:crypto';

// Run only on the owner's trusted machine. Prints the new runtime credential
// once for transfer to the tablet; the server keeps only its SHA-256 digest.
const token = `auron_local_${randomBytes(32).toString('base64url')}`;
process.stdout.write(`Token pessoal para inserir somente no tablet:\n${token}\n\n`);
process.stdout.write(`No .env privado do servidor:\nLOCAL_ACCESS_TOKEN_SHA256=${createHash('sha256').update(token).digest('hex')}\n`);
process.stdout.write('\nNão envie o token em conversas, logs, commits ou capturas de tela.\n');
