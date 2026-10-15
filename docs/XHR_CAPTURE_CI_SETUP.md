# XHR_CAPTURE_CI_SETUP.md

## Approach
Set up emulator + traffic capture in GitHub Actions runner (ubuntu-latest). Options: Linux with KVM, or self-hosted runner (Windows/Linux) with Android emulator + mitmproxy. Also can use nested runner.

## What we added
- Workflow: `.github/workflows/xhr-capture.yml` - dispatchable, starts AVD (android-30), installs mitmproxy, prepares capture dirs, uploads artifacts

## Feasibility
- GitHub-hosted ubuntu-latest: works if `/dev/kvm` available (same as instrumented). If not, emulator may be slow/unreliable - report as untested with reason.
- Better: self-hosted runner on Windows/Linux with KVM or physical Android device connected via ADB over network.
- Alternative: mitmproxy on host + proxying device on same network (if we had device access).

## Practical setup for actual capture
1. Trigger `xhr-capture` workflow manually (workflow_dispatch)
2. If KVM available: emulator boots, can install apps (ConfirmTkt/RailYatri/ixigo APKs) and run mitmproxy as proxy
3. Capture flows to `mitmproxy_flows/`, extract JSON responses to `poc/fixtures/cands_*_live/`
4. Artifacts uploaded for download

## Notes
- Emulator needs proxy configured to mitmproxy (usually localhost:8080)
- Need to install mitmproxy CA cert on emulator system store for HTTPS
- Installing target apps in emulator requires APKs or Play Store (Play Store needs setup)
- For reliability: self-hosted runner is better; in cloud runners, KVM is the limiting factor
