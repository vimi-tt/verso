package elovaire.music.droidbeauty.app.ui.i18n

import elovaire.music.droidbeauty.app.domain.model.AppLanguage

internal data class ArtistFollowUiCopy(
    val follow: String,
    val following: String,
    val followArtistCdTemplate: String,
    val unfollowArtistCdTemplate: String,
) {
    fun followCd(artistName: String): String = followArtistCdTemplate.replace("%s", artistName)
    fun unfollowCd(artistName: String): String = unfollowArtistCdTemplate.replace("%s", artistName)
}

@Suppress("CyclomaticComplexMethod")
internal fun artistFollowCopy(language: AppLanguage): ArtistFollowUiCopy = when (language) {
    AppLanguage.English -> ArtistFollowUiCopy("Follow", "Following", "Follow %s", "Unfollow %s")
    AppLanguage.Portuguese -> ArtistFollowUiCopy("Seguir", "Seguindo", "Seguir %s", "Deixar de seguir %s")
    AppLanguage.Spanish -> ArtistFollowUiCopy("Seguir", "Siguiendo", "Seguir a %s", "Dejar de seguir a %s")
    AppLanguage.French -> ArtistFollowUiCopy("Suivre", "Abonné", "Suivre %s", "Ne plus suivre %s")
    AppLanguage.German -> ArtistFollowUiCopy("Folgen", "Folge ich", "%s folgen", "%s entfolgen")
    AppLanguage.Italian -> ArtistFollowUiCopy("Segui", "Seguito", "Segui %s", "Smetti di seguire %s")
    AppLanguage.Dutch -> ArtistFollowUiCopy("Volgen", "Volgend", "%s volgen", "%s ontvolgen")
    AppLanguage.Polish -> ArtistFollowUiCopy("Obserwuj", "Obserwujesz", "Obserwuj %s", "Przestań obserwować %s")
    AppLanguage.Russian -> ArtistFollowUiCopy("Подписаться", "Вы подписаны", "Подписаться на %s", "Отписаться от %s")
    AppLanguage.Ukrainian -> ArtistFollowUiCopy("Підписатися", "Ви підписані", "Підписатися на %s", "Відписатися від %s")
    AppLanguage.Japanese -> ArtistFollowUiCopy("フォロー", "フォロー中", "%sをフォロー", "%sのフォローを解除")
    AppLanguage.Korean -> ArtistFollowUiCopy("팔로우", "팔로잉", "%s 팔로우", "%s 팔로우 취소")
    AppLanguage.ChineseSimplified -> ArtistFollowUiCopy("关注", "已关注", "关注 %s", "取消关注 %s")
    AppLanguage.Hindi -> ArtistFollowUiCopy("फ़ॉलो करें", "फ़ॉलो कर रहे हैं", "%s को फ़ॉलो करें", "%s को अनफ़ॉलो करें")
    AppLanguage.Bengali -> ArtistFollowUiCopy("অনুসরণ করুন", "অনুসরণ করছেন", "%s-কে অনুসরণ করুন", "%s-কে অনুসরণ বাদ দিন")
    AppLanguage.Punjabi -> ArtistFollowUiCopy("ਫਾਲੋ ਕਰੋ", "ਫਾਲੋ ਕਰ ਰਹੇ ਹੋ", "%s ਨੂੰ ਫਾਲੋ ਕਰੋ", "%s ਨੂੰ ਅਨਫਾਲੋ ਕਰੋ")
    AppLanguage.Urdu -> ArtistFollowUiCopy("فالو کریں", "فالو کر رہے ہیں", "%s کو فالو کریں", "%s کو ان فالو کریں")
    AppLanguage.Indonesian -> ArtistFollowUiCopy("Ikuti", "Mengikuti", "Ikuti %s", "Berhenti mengikuti %s")
    AppLanguage.Malay -> ArtistFollowUiCopy("Ikut", "Mengikuti", "Ikut %s", "Nyahikut %s")
    AppLanguage.Thai -> ArtistFollowUiCopy("ติดตาม", "กำลังติดตาม", "ติดตาม %s", "เลิกติดตาม %s")
    AppLanguage.Albanian -> ArtistFollowUiCopy("Ndiq", "Duke ndjekur", "Ndiq %s", "Hiq ndjekjen e %s")
    AppLanguage.Bosnian -> ArtistFollowUiCopy("Prati", "Pratite", "Prati %s", "Prestani pratiti %s")
    AppLanguage.Bulgarian -> ArtistFollowUiCopy("Последвай", "Следвате", "Последвай %s", "Спри да следваш %s")
    AppLanguage.Catalan -> ArtistFollowUiCopy("Segueix", "Seguint", "Segueix %s", "Deixa de seguir %s")
    AppLanguage.Croatian -> ArtistFollowUiCopy("Prati", "Pratite", "Prati %s", "Prestani pratiti %s")
    AppLanguage.Czech -> ArtistFollowUiCopy("Sledovat", "Sledujete", "Sledovat %s", "Přestat sledovat %s")
    AppLanguage.Danish -> ArtistFollowUiCopy("Følg", "Følger", "Følg %s", "Følg ikke %s")
    AppLanguage.Estonian -> ArtistFollowUiCopy("Jälgi", "Jälgid", "Jälgi %s", "Lõpeta %s jälgimine")
    AppLanguage.Finnish -> ArtistFollowUiCopy("Seuraa", "Seurataan", "Seuraa %s", "Lopeta %s seuraaminen")
    AppLanguage.Greek -> ArtistFollowUiCopy("Ακολούθησε", "Ακολουθείτε", "Ακολούθησε %s", "Διακοπή παρακολούθησης %s")
    AppLanguage.Hungarian -> ArtistFollowUiCopy("Követés", "Követve", "%s követése", "%s követésének leállítása")
    AppLanguage.Latvian -> ArtistFollowUiCopy("Sekot", "Sekojat", "Sekot %s", "Pārtraukt sekot %s")
    AppLanguage.Lithuanian -> ArtistFollowUiCopy("Sekti", "Sekate", "Sekti %s", "Nebesekti %s")
    AppLanguage.Macedonian -> ArtistFollowUiCopy("Следи", "Следите", "Следи %s", "Откажи следење на %s")
    AppLanguage.Norwegian -> ArtistFollowUiCopy("Følg", "Følger", "Følg %s", "Slutt å følge %s")
    AppLanguage.Romanian -> ArtistFollowUiCopy("Urmărește", "Urmărești", "Urmărește %s", "Nu mai urmări %s")
    AppLanguage.Serbian -> ArtistFollowUiCopy("Прати", "Пратите", "Прати %s", "Престани да пратиш %s")
    AppLanguage.Slovak -> ArtistFollowUiCopy("Sledovať", "Sledujete", "Sledovať %s", "Prestať sledovať %s")
    AppLanguage.Slovenian -> ArtistFollowUiCopy("Sledi", "Sledite", "Sledi %s", "Prenehaj slediti %s")
    AppLanguage.Swedish -> ArtistFollowUiCopy("Följ", "Följer", "Följ %s", "Sluta följa %s")
}
