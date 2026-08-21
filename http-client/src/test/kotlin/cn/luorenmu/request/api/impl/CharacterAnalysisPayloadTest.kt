package cn.luorenmu.request.api.impl

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CharacterAnalysisPayloadTest {
    @Test
    fun `extracts next data when attributes are reordered and use single quotes`() {
        val payload = """{"props":{"pageProps":{}}}"""
        val html = """
            <html><body>
            <script nonce='abc' type='application/json' id='__NEXT_DATA__'>$payload</script>
            </body></html>
        """.trimIndent()

        assertEquals(payload, EternalReturnDakGGApi.extractCharacterAnalysisPayload(html))
    }

    @Test
    fun `falls back to statistics json when next data id is absent`() {
        val payload = """{"props":{"pageProps":{"dehydratedState":{"queries":[{"state":{"data":{"characterDetailStatSnapshot":null}}}]}}}}"""
        val html = """<script type="application/json" data-page="character">$payload</script>"""

        assertEquals(payload, EternalReturnDakGGApi.extractCharacterAnalysisPayload(html))
    }

    @Test
    fun `live character page remains parseable`() = runBlocking {
        if (System.getenv("REAL_CHARACTER_ANALYSIS") != "true") return@runBlocking

        val response = EternalReturnDakGGApi.GetCharacterAnalysis(
            characterKey = "1",
            teamMode = "SQUAD",
            matchingMode = "RANK",
            tier = "diamond_plus",
        ).execute()
        val page = response.props.pageProps

        assertTrue(page.character.name.isNotBlank())
        assertNotNull(
            page.dehydratedState.queries
                .firstNotNullOfOrNull { it.state.data.characterDetailStatSnapshot }
        )
    }
}
