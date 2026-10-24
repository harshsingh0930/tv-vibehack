import { passwordHash } from './domain.mjs';
import { Writable } from 'node:stream';
import { createInterface } from 'node:readline/promises';
if (!process.stdin.isTTY) {
  let value = '';
  for await (const chunk of process.stdin) value += chunk;
  if (value.trim().length < 8) throw new Error('Use at least eight characters.');
  process.stdout.write(passwordHash(value.trim()) + '\n');
} else {
  process.stdout.write('Choose an owner password (8+ characters; input hidden): ');
  const sink = new Writable({
    write(_chunk, _encoding, done) {
      done();
    },
  });
  const reader = createInterface({ input: process.stdin, output: sink, terminal: true });
  const value = await reader.question('');
  reader.close();
  process.stdout.write('\n');
  if (value.length < 8) throw new Error('Use at least eight characters.');
  process.stdout.write('OWNER_PASSWORD_HASH=' + passwordHash(value) + '\n');
}
