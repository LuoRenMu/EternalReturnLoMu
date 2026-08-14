package cn.luorenmu.service

import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharactersResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGProfileResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileCharacterImageRequestsTest {
    @Test
    fun `collects characters from every overview and duo stats`() {
        val profile = DakGGProfileResponse(
            meta = DakGGProfileResponse.ProfileMeta(),
            player = DakGGProfileResponse.ProfilePlayer(),
            playerSeasonOverviews = listOf(
                overview(characterId = 1, skinId = 101),
                overview(characterId = 2, skinId = 202, duoCharacterId = 3),
            ),
        )
        val characters = DakGGCharactersResponse(arrayListOf(
            character(1, 101),
            character(2, 202),
            character(3, 303),
        ))

        val requests = profileCharacterImageRequests(profile, characters)

        assertEquals(listOf(1L to 101L, 2L to 202L, 3L to 303L), requests.map { it.first.id to it.second })
    }

    @Test
    fun `collects default avatar skin when profile uses another skin`() {
        val profile = DakGGProfileResponse(
            meta = DakGGProfileResponse.ProfileMeta(),
            player = DakGGProfileResponse.ProfilePlayer(),
            playerSeasonOverviews = listOf(overview(characterId = 1, skinId = 199)),
        )
        val characters = DakGGCharactersResponse(arrayListOf(character(1, 101)))

        val requests = profileCharacterImageRequests(profile, characters)

        assertEquals(listOf(1L to 199L, 1L to 101L), requests.map { it.first.id to it.second })
    }

    private fun overview(characterId: Long, skinId: Long, duoCharacterId: Long? = null) =
        DakGGProfileResponse.ProfilePlayerSeasonOverviews(
            characterStats = listOf(
                DakGGProfileResponse.ProfilePlayerSeasonOverviews.ProfileStat(
                    key = characterId,
                    skinStats = listOf(DakGGProfileResponse.ProfilePlayerSeasonOverviews.ProfileStat(key = skinId)),
                )
            ),
            mmrStats = emptyList(),
            duoStats = duoCharacterId?.let { id ->
                listOf(
                    DakGGProfileResponse.ProfilePlayerSeasonOverviews.ProfileDuoStat(
                        userNum = 1,
                        nickname = "duo",
                        updatedAt = 0,
                        play = 1,
                        win = 0,
                        place = 1,
                        characterStats = listOf(
                            DakGGProfileResponse.ProfilePlayerSeasonOverviews.ProfileDuoStat.ProfileCharacterStat(key = id)
                        ),
                    )
                )
            }.orEmpty(),
            recentMatches = emptyList(),
        )

    private fun character(id: Long, skinId: Long) = DakGGCharactersResponse.DakGGCharacterById(
        id = id,
        skins = listOf(DakGGCharactersResponse.DakGGCharacterById.DakGGSkin(id = skinId)),
    )
}
