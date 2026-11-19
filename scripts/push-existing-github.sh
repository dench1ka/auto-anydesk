#!/bin/sh
# Push sources and the prebuilt APK with ordinary git and the user's configured credentials.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
repo_url=https://github.com/dench1ka/auto-anydesk.git
upload_dir=$(mktemp -d "${TMPDIR:-/tmp}/auto-anydesk-upload.XXXXXX")
printf 'Working checkout: %s\n' "$upload_dir/repository"
git clone --single-branch --branch main "$repo_url" "$upload_dir/repository"
python3 "$project_dir/scripts/publish-github.py" --prepare-only
cp -R "$project_dir/artifacts/github-upload/." "$upload_dir/repository/"
git -C "$upload_dir/repository" add .
if ! git -C "$upload_dir/repository" diff --cached --quiet; then
    git -C "$upload_dir/repository" commit -m 'Add AnyDesk Auto Confirm sources, API 25 debug APK and instructions'
fi
git -C "$upload_dir/repository" push origin HEAD:main
apk_name=$(cd "$project_dir" && python3 -c 'import sys; sys.path.insert(0, "scripts"); from project_config import APK_NAME; print(APK_NAME)')
printf '\nAPK: https://github.com/dench1ka/auto-anydesk/raw/refs/heads/main/artifacts/%s\n' "$apk_name"
