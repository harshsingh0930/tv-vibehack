#!/bin/sh
# Read-only inspection. Never prints keys, environment files, tokens or VPN configs.
set -eu
printf '\n--- Server ---\n'
id
uname -sm
if [ -f /etc/os-release ]; then grep '^PRETTY_NAME=' /etc/os-release; fi
printf '\n--- Capacity ---\n'
df -h /opt /var
if command -v free >/dev/null 2>&1; then free -m; fi
printf '\n--- Existing web ports ---\n'
if command -v ss >/dev/null 2>&1; then ss -ltn '( sport = :80 or sport = :443 or sport = :8787 )'; fi
printf '\n--- Runtime ---\n'
if command -v node >/dev/null 2>&1; then node --version; else printf 'Node not installed\n'; fi
if command -v npm >/dev/null 2>&1; then npm --version; fi
if command -v caddy >/dev/null 2>&1; then caddy version; else printf 'Caddy not installed\n'; fi
printf '\n--- VPN interface addresses ---\n'
if command -v ip >/dev/null 2>&1; then ip -brief -4 address; fi
printf '\nNo configuration or firewall rules were changed.\n'
