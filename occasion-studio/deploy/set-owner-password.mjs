// Run interactively as root on Oracle; never accepts a password in arguments.
import { createInterface } from "node:readline/promises";
import { Writable } from "node:stream";
import { readFileSync, writeFileSync, renameSync, chmodSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { passwordHash } from "/opt/occasion-studio/cloud/server/domain.mjs";

if (process.getuid?.() !== 0 || !process.stdin.isTTY) {
  console.error("Run this command with sudo in an interactive SSH terminal.");
  process.exit(1);
}

const silent = new Writable({
  write(_chunk, _encoding, done) {
    done();
  },
});
const reader = createInterface({
  input: process.stdin,
  output: silent,
  terminal: true,
});
try {
  process.stdout.write(
    "Choose an owner password (8+ characters; typing is hidden): ",
  );
  const password = await reader.question("");
  process.stdout.write("\nConfirm password: ");
  const confirmation = await reader.question("");
  process.stdout.write("\n");
  if (
    password.length < 8 ||
    password.length > 1024 ||
    password !== confirmation
  ) {
    console.error(
      "Passwords must match and contain 8 to 1024 characters. Nothing changed.",
    );
    process.exitCode = 1;
  } else {
    const path = "/etc/occasion-studio.env";
    const contents = readFileSync(path, "utf8");
    if (!/^OWNER_PASSWORD_HASH=.*$/m.test(contents))
      throw new Error("Missing password configuration.");
    const temporary = path + ".new";
    writeFileSync(
      temporary,
      contents.replace(
        /^OWNER_PASSWORD_HASH=.*$/m,
        "OWNER_PASSWORD_HASH=" + passwordHash(password),
      ),
      { mode: 0o600, flag: "wx" },
    );
    chmodSync(temporary, 0o600);
    renameSync(temporary, path);
    const reset = spawnSync(
      "/usr/sbin/runuser",
      [
        "-u",
        "occasion",
        "--",
        "/usr/local/bin/node",
        "--input-type=module",
        "-e",
        'import {DatabaseSync} from "node:sqlite"; const db=new DatabaseSync("/var/lib/occasion-studio/studio.sqlite"); db.exec("DELETE FROM sessions"); db.close();',
      ],
      { encoding: "utf8" },
    );
    if (reset.status !== 0)
      throw new Error(
        "Password saved but session reset failed; contact the administrator.",
      );
    const restart = spawnSync(
      "/usr/bin/systemctl",
      ["restart", "occasion-studio.service"],
      { encoding: "utf8" },
    );
    if (restart.status !== 0)
      throw new Error("Password saved but the service could not restart.");
    console.log("Password saved. Sign in at https://occasion.example.com");
    console.log(
      "Existing dashboard sessions were signed out. TV pairings are unchanged.",
    );
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  reader.close();
}
