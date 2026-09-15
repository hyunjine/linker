package com.hyunjine.linker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Releases API 클라이언트. 앱 드로워 "릴리즈 노트" 항목에서 진입할 때마다 fetch (#255).
 *
 * 공용 저장소라 인증 없이 호출 가능 (rate limit 시간당 60req/ip). draft · prerelease 는 앱에서
 * 걸러낸다 (Repository 는 원본 그대로 반환).
 *
 * 호출 URL: `GET https://api.github.com/repos/hyunjine/linker/releases`
 */
class ReleasesRepository(
    private val client: HttpClient = defaultClient(),
) {
    /**
     * 모든 릴리즈를 **최신 → 과거 순** 으로 반환. GitHub API 응답 기본 순서와 동일하지만 안전을
     * 위해 명시적으로 정렬. 최신 버전이 리스트 상단에 오도록 정렬 결과 유지 (#255).
     */
    suspend fun fetchAll(): List<GithubRelease> {
        val list: List<GithubRelease> = client
            .get(RELEASES_URL) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            .body()
        return list.sortedByDescending { it.publishedAt.orEmpty() }
    }

    /**
     * 관심 필드만 뽑아 매핑. `body` 는 GitHub 마크다운 (섹션 헤더 `## X` + 불릿 `- X`) 그대로.
     * 앱은 자체 파서로 렌더 (헤더/불릿만 지원).
     */
    @Serializable
    data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        /** ISO 8601 UTC. 정렬 · 화면 표시 (앞 10자만 = yyyy-MM-dd) 에 사용. */
        @SerialName("published_at") val publishedAt: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/hyunjine/linker/releases"

        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }
}
