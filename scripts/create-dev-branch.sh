#!/usr/bin/env bash
# 다음 버전 개발 브랜치를 연다 (#433).
#   1. release (지금 TestFlight 에 나간 코드) 끝에서 develop/vX.Y.Z 생성
#   2. 첫 커밋으로 Config.xcconfig 의 MARKETING_VERSION=X.Y.Z
#   3. 레포 default branch 를 develop/vX.Y.Z 로 변경
#
# 사용: scripts/create-dev-branch.sh X.Y.Z [--source <브랜치>] [--yes]
#   --source  갈라질 브랜치 (기본: release)
#   --yes     확인 질문을 건너뛴다
#
# 이전 개발 브랜치 (지금 default branch) 에만 있는 커밋 (릴리즈 뒤 머지) 은 새 브랜치에
# 안 들어간다 — 그 개수와 목록을 보여 주고, 있으면 진행할지 한 번 더 묻는다.
# 그 커밋을 가져가려면 --source 로 이전 개발 브랜치를 준다.
#
# 레포 Admin 만 쓸 수 있다 — default branch 변경이 Admin 권한이다.
set -euo pipefail

XCCONFIG=iosApp/Configuration/Config.xcconfig

die() { echo "error: $*" >&2; exit 1; }

confirm() {
  [ "$YES" = true ] && return 0
  local answer
  read -r -p "$1 [y/N] " answer
  [[ "$answer" =~ ^[yY]$ ]] || die "취소했다"
}

VERSION=""
SOURCE=""
YES=false
while [ $# -gt 0 ]; do
  case "$1" in
    --source) [ -n "${2:-}" ] || die "--source 에 브랜치 이름이 필요하다"; SOURCE="$2"; shift 2 ;;
    --yes) YES=true; shift ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) [ -z "$VERSION" ] || die "인자가 너무 많다: $1"; VERSION="${1#v}"; shift ;;
  esac
done
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "버전은 X.Y.Z 형식이어야 한다 (받은 값: '${VERSION}')"
BRANCH="develop/v$VERSION"

cd "$(git rev-parse --show-toplevel)"

# 확인
[ "$(gh repo view --json viewerPermission -q .viewerPermission)" = ADMIN ] || die "레포 Admin 만 실행할 수 있다"
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "커밋하지 않은 변경이 있다 — 정리한 뒤 다시 실행한다"
git fetch -q origin
if git ls-remote --exit-code --heads origin "$BRANCH" > /dev/null; then
  die "origin/$BRANCH 가 이미 있다"
fi
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  die "로컬에 $BRANCH 가 있다 — 지운 뒤 다시 실행한다 (git branch -D $BRANCH)"
fi
if git ls-remote --exit-code --tags origin "v$VERSION" > /dev/null; then
  die "v$VERSION 은 이미 릴리즈된 버전이다 (태그가 있다)"
fi

[ -n "$SOURCE" ] || SOURCE=release
git rev-parse --verify --quiet "origin/$SOURCE" > /dev/null || die "origin/$SOURCE 가 없다"
OLD_DEFAULT=$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)
git rev-parse --verify --quiet "origin/$OLD_DEFAULT" > /dev/null || die "origin/$OLD_DEFAULT 가 없다"
CURRENT=$(git show "origin/$SOURCE:$XCCONFIG" | sed -n 's/^MARKETING_VERSION=//p' | tr -d '[:space:]')

# 새 버전이 지금보다 큰지 (sort -V 로 비교)
if [ "$CURRENT" = "$VERSION" ] || [ "$(printf '%s\n%s\n' "$CURRENT" "$VERSION" | sort -V | tail -1)" != "$VERSION" ]; then
  confirm "새 버전 $VERSION 이 $SOURCE 의 MARKETING_VERSION $CURRENT 보다 크지 않다. 계속할까?"
fi

echo "origin/$SOURCE → $BRANCH"
echo "MARKETING_VERSION $CURRENT → $VERSION"
echo "default branch $OLD_DEFAULT → $BRANCH"

# 이전 개발 브랜치에만 있는 커밋 — 새 브랜치에 안 들어간다
MISSING=0
if [ "$OLD_DEFAULT" != "$SOURCE" ]; then
  MISSING=$(git rev-list --count --no-merges "origin/$SOURCE..origin/$OLD_DEFAULT")
fi
echo "$OLD_DEFAULT 에만 있는 커밋 (새 브랜치에 안 들어감): $MISSING 개"
if [ "$MISSING" -gt 0 ]; then
  git --no-pager log -n 10 --no-merges --format='  %h %s' "origin/$SOURCE..origin/$OLD_DEFAULT"
  [ "$MISSING" -le 10 ] || echo "  … 외 $((MISSING - 10)) 개"
  confirm "$OLD_DEFAULT 의 커밋 $MISSING 개 없이 진행할까?"
fi
confirm "진행할까?"

# 실행
git switch -q -c "$BRANCH" "origin/$SOURCE"
tmp=$(mktemp)
sed "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$VERSION/" "$XCCONFIG" > "$tmp"
mv "$tmp" "$XCCONFIG"
[ "$(git diff --numstat | awk '{ add += $1; del += $2 } END { print add "/" del }')" = "1/1" ] ||
  die "$XCCONFIG 가 한 줄만 바뀌지 않았다 — git diff 로 확인한다"
git commit -q -am "chore: 버전 $VERSION 개발 시작"
git push -q -u origin "$BRANCH" ||
  die "push 에 실패했다 — 로컬 $BRANCH 와 버전 커밋은 남아 있다. 원인을 고친 뒤 git push -u origin $BRANCH 와 gh repo edit --default-branch $BRANCH 를 직접 한다"
gh repo edit --default-branch "$BRANCH" > /dev/null ||
  die "$BRANCH 는 push 됐지만 default branch 변경에 실패했다 — gh repo edit --default-branch $BRANCH 로 직접 바꾼다"

# 결과
DEFAULT=$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)
PUSHED=$(git show "origin/$BRANCH:$XCCONFIG" | sed -n 's/^MARKETING_VERSION=//p' | tr -d '[:space:]')
echo
echo "branch:            $BRANCH ($(git rev-parse --short HEAD))"
echo "MARKETING_VERSION: $PUSHED"
echo "default branch:    $DEFAULT"
[ "$DEFAULT" = "$BRANCH" ] && [ "$PUSHED" = "$VERSION" ] || die "결과가 예상과 다르다 — 위 값을 확인한다"
