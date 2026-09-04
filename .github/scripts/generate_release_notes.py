#!/usr/bin/env python3
"""stdin 으로 받은 `<hash> <subject>` 목록을 Claude 로 GitHub Release 노트로 정리.

사용:
    git log v1.0.0..HEAD --no-merges --pretty=format:'%h %s' \
      | python generate_release_notes.py > notes.md

환경변수:
    ANTHROPIC_API_KEY  Claude API key (repo secret 으로 워크플로가 주입)

결정 사항:
    - 모델: claude-sonnet-4-6 (릴리즈 노트 정리에 충분한 품질/비용)
    - 카테고리: 신규 기능 · 개선 · 버그 수정 · 기타
    - 톤: 한국어 · 담백 · 사용자 관점
    - 각 항목 뒤에 커밋 해시 [#해시]
"""
from __future__ import annotations

import os
import sys

from anthropic import Anthropic

MODEL = "claude-sonnet-4-6"

PROMPT_TEMPLATE = """아래 git 커밋 목록을 GitHub Release 노트로 정리해줘.

원칙:
- 카테고리별 그룹 헤더 (`## 신규 기능` / `## 개선` / `## 버그 수정` / `## 기타`) — 항목이 있는 카테고리만 포함
- 각 항목은 짧고 사용자 관점 (내부 리팩터 · chore · CI 는 '기타' 로)
- 커밋 subject 앞의 conventional prefix (feat/fix/chore/refactor/docs/style/build/ci/perf/test) 는 제거
- 커밋 subject 앞의 `[#이슈번호]` 는 유지해도 되지만, 항목 텍스트 흐름에 어색하면 제거
- 마지막에 원본 커밋 해시를 `[#해시]` 로 붙임 (여러 커밋이 하나 항목이 되면 여러 개 나열)
- 한국어 · 존댓말 없이 담백한 톤 (예: "Apple 로그인 추가", "프로필 저장 실패 수정")
- 서두에 인사말이나 요약 문장 없이 카테고리 헤더부터 시작
- 완전히 무의미한 커밋 (버전 bump, merge 표식 등) 은 생략

git log:
{commits}
"""


def main() -> int:
    commits = sys.stdin.read().strip()
    if not commits:
        print("_이번 릴리즈에 변경사항이 없습니다._")
        return 0

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("::error::ANTHROPIC_API_KEY 미설정 (repo secret 확인)", file=sys.stderr)
        return 1

    client = Anthropic(api_key=api_key)
    resp = client.messages.create(
        model=MODEL,
        max_tokens=2048,
        messages=[{"role": "user", "content": PROMPT_TEMPLATE.format(commits=commits)}],
    )
    # Claude API 응답은 content blocks 배열. 우리는 단일 text block 만 기대.
    text_parts = [b.text for b in resp.content if getattr(b, "type", None) == "text"]
    print("\n".join(text_parts).strip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
