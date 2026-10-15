#!/usr/bin/env bash
set -euo pipefail
case "$(< /proc/self/cgroup)" in
  *heavy.slice*) ;;
  *) printf '%s\n' 'Run this launcher through heavy after checking heavy status.' >&2; exit 2 ;;
esac
export LD_LIBRARY_PATH=/tmp/icon-ideas/runtime-r3/blender-5.1.1-linux-x64/lib:/nix/store/764nssak6zdj6c0gx7yhh86gv912nhin-steam-run-1.0.0.85-fhsenv-rootfs/usr/lib64:/nix/store/m22zkzqhpdcj9cpb8jwrn0dgkm1w0m25-libsm-1.2.6/lib:/nix/store/bynz5jwnhmp8bpv2l0jj3iar96ala6xm-libice-1.1.2/lib
exec /tmp/icon-ideas/runtime-r3/blender-5.1.1-linux-x64/blender "$@"
