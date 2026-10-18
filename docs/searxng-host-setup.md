# Hosting SearXNG for Iris (this workstation — ElyssiaAI)

## Runtime architecture (recorded 2026-10-18)

- Native install: `/home/elyssia/opt/searxng/searxng` (code) + `/home/elyssia/opt/searxng/env` (python).
- Runs as **systemd user unit** `searxng.service`
  (`~/.config/systemd/user/searxng.service`):
  - `WorkingDirectory=/home/elyssia/opt/searxng/searxng`
  - `ExecStart=/home/elyssia/opt/searxng/env/bin/python searx/webapp.py`
  - `Environment=SEARXNG_SETTINGS_PATH=/etc/searxng/settings.yml` (unit file default)
- `/etc/searxng/settings.yml` is root-owned 644 (needs sudo to edit; passwordless
  sudo not available in the automation path).

## Changes made for Iris (all user-level, no root)

1. Repo config `/home/elyssia/opt/searxng/searxng/searx/settings.yml`:
   - `server.bind_address`: `127.0.0.1` → `0.0.0.0` (serves loopback + LAN
     192.168.31.65 + Tailscale 100.110.99.98).
   - `search.formats`: added `- json` under `- html` (JSON API for the app;
     omitting it makes non-loopback `?format=json` return 403).
   - `server.secret_key`: replaced placeholder `ultrasecretkey` with the real
     value from `/etc/searxng/settings.yml` (login refuses the placeholder and
     crashes the app on boot).
   - Backup kept: `settings.yml.bak` (pre-change state).
2. New drop-in override `~/.config/systemd/user/searxng.service.d/override.conf`:

   ```ini
   [Service]
   Environment=SEARXNG_SETTINGS_PATH=/home/elyssia/opt/searxng/searxng/searx/settings.yml
   ```

   (`systemctl --user daemon-reload && systemctl --user restart searxng.service`)
   `/etc/searxng/settings.yml` was left untouched.
3. Candidate URLs the app probes, in order: Tailscale `100.110.99.98:8888` →
   LAN `192.168.31.65:8888` → loopback `127.0.0.1:8888` (USB/adb reverse).

## Verification (passed)

```
127.0.0.1      probe=200 json=200
100.110.99.98  probe=200 json=200   (Tailscale)
192.168.31.65  probe=200 json=200   (LAN)
```

## Revert

```bash
cp /home/elyssia/opt/searxng/searxng/searx/settings.yml.bak \
   /home/elyssia/opt/searxng/searxng/searx/settings.yml
rm ~/.config/systemd/user/searxng.service.d/override.conf
systemctl --user daemon-reload && systemctl --user restart searxng.service
```

Returns the instance to the unit-file default (`/etc/searxng/settings.yml`,
loopback-only, html-only).
