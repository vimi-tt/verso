package elovaire.music.droidbeauty.app.ui.i18n

import elovaire.music.droidbeauty.app.domain.model.AppLanguage

internal data class YouTubeDownloadUiCopy(
    val downloadsTitle: String,
    val downloadsInProgress: String,
    val noActiveDownloads: String,
    val noDownloadedSongs: String,
    val downloadAction: String,
    val downloadedAction: String,
    val downloadingAction: String,
    val removeDownloadAction: String,
)

@Suppress("CyclomaticComplexMethod")
internal fun youtubeDownloadCopy(language: AppLanguage): YouTubeDownloadUiCopy = when (language) {
    AppLanguage.English -> YouTubeDownloadUiCopy(
        downloadsTitle = "Downloads",
        downloadsInProgress = "Downloads in progress",
        noActiveDownloads = "No downloads in progress",
        noDownloadedSongs = "No downloaded songs yet",
        downloadAction = "Download",
        downloadedAction = "Downloaded",
        downloadingAction = "Downloading...",
        removeDownloadAction = "Remove download",
    )
    AppLanguage.Portuguese -> YouTubeDownloadUiCopy(
        downloadsTitle = "Downloads",
        downloadsInProgress = "Downloads em progresso",
        noActiveDownloads = "Nenhum download em andamento",
        noDownloadedSongs = "Nenhuma música baixada ainda",
        downloadAction = "Baixar",
        downloadedAction = "Baixada",
        downloadingAction = "Baixando...",
        removeDownloadAction = "Remover download",
    )
    AppLanguage.Spanish -> YouTubeDownloadUiCopy(
        downloadsTitle = "Descargas",
        downloadsInProgress = "Descargas en curso",
        noActiveDownloads = "No hay descargas en curso",
        noDownloadedSongs = "Aún no hay canciones descargadas",
        downloadAction = "Descargar",
        downloadedAction = "Descargada",
        downloadingAction = "Descargando...",
        removeDownloadAction = "Eliminar descarga",
    )
    AppLanguage.French -> YouTubeDownloadUiCopy(
        downloadsTitle = "Téléchargements",
        downloadsInProgress = "Téléchargements en cours",
        noActiveDownloads = "Aucun téléchargement en cours",
        noDownloadedSongs = "Aucun titre téléchargé pour l'instant",
        downloadAction = "Télécharger",
        downloadedAction = "Téléchargé",
        downloadingAction = "Téléchargement...",
        removeDownloadAction = "Supprimer le téléchargement",
    )
    AppLanguage.German -> YouTubeDownloadUiCopy(
        downloadsTitle = "Downloads",
        downloadsInProgress = "Laufende Downloads",
        noActiveDownloads = "Keine laufenden Downloads",
        noDownloadedSongs = "Noch keine heruntergeladenen Songs",
        downloadAction = "Herunterladen",
        downloadedAction = "Heruntergeladen",
        downloadingAction = "Wird heruntergeladen...",
        removeDownloadAction = "Download entfernen",
    )
    AppLanguage.Italian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Download",
        downloadsInProgress = "Download in corso",
        noActiveDownloads = "Nessun download in corso",
        noDownloadedSongs = "Nessun brano scaricato",
        downloadAction = "Scarica",
        downloadedAction = "Scaricato",
        downloadingAction = "Download in corso...",
        removeDownloadAction = "Rimuovi download",
    )
    AppLanguage.Japanese -> YouTubeDownloadUiCopy(
        downloadsTitle = "ダウンロード",
        downloadsInProgress = "進行中のダウンロード",
        noActiveDownloads = "進行中のダウンロードはありません",
        noDownloadedSongs = "ダウンロード済みの曲はまだありません",
        downloadAction = "ダウンロード",
        downloadedAction = "ダウンロード済み",
        downloadingAction = "ダウンロード中...",
        removeDownloadAction = "ダウンロードを削除",
    )
    AppLanguage.Korean -> YouTubeDownloadUiCopy(
        downloadsTitle = "오프라인 저장",
        downloadsInProgress = "진행 중인 다운로드",
        noActiveDownloads = "진행 중인 다운로드가 없습니다",
        noDownloadedSongs = "저장된 곡이 아직 없습니다",
        downloadAction = "다운로드",
        downloadedAction = "저장됨",
        downloadingAction = "다운로드 중...",
        removeDownloadAction = "다운로드 삭제",
    )
    AppLanguage.ChineseSimplified -> YouTubeDownloadUiCopy(
        downloadsTitle = "下载",
        downloadsInProgress = "正在下载",
        noActiveDownloads = "没有正在进行的下载",
        noDownloadedSongs = "还没有已下载的歌曲",
        downloadAction = "下载",
        downloadedAction = "已下载",
        downloadingAction = "正在下载...",
        removeDownloadAction = "移除下载",
    )
    AppLanguage.Russian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Загрузки",
        downloadsInProgress = "Активные загрузки",
        noActiveDownloads = "Нет активных загрузок",
        noDownloadedSongs = "Пока нет загруженных треков",
        downloadAction = "Скачать",
        downloadedAction = "Скачано",
        downloadingAction = "Загрузка...",
        removeDownloadAction = "Удалить загрузку",
    )
    AppLanguage.Ukrainian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Завантаження",
        downloadsInProgress = "Тривають завантаження",
        noActiveDownloads = "Немає активних завантажень",
        noDownloadedSongs = "Ще немає завантажених треків",
        downloadAction = "Завантажити",
        downloadedAction = "Завантажено",
        downloadingAction = "Завантаження...",
        removeDownloadAction = "Видалити завантаження",
    )
    AppLanguage.Polish -> YouTubeDownloadUiCopy(
        downloadsTitle = "Pobrane",
        downloadsInProgress = "Trwające pobierania",
        noActiveDownloads = "Brak trwających pobrań",
        noDownloadedSongs = "Brak pobranych utworów",
        downloadAction = "Pobierz",
        downloadedAction = "Pobrano",
        downloadingAction = "Pobieranie...",
        removeDownloadAction = "Usuń pobrane",
    )
    AppLanguage.Dutch -> YouTubeDownloadUiCopy(
        downloadsTitle = "Downloads",
        downloadsInProgress = "Actieve downloads",
        noActiveDownloads = "Geen actieve downloads",
        noDownloadedSongs = "Nog geen gedownloade nummers",
        downloadAction = "Downloaden",
        downloadedAction = "Gedownload",
        downloadingAction = "Downloaden...",
        removeDownloadAction = "Download verwijderen",
    )
    AppLanguage.Albanian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Shkarkimet",
        downloadsInProgress = "Shkarkime në proces",
        noActiveDownloads = "Nuk ka shkarkime në proces",
        noDownloadedSongs = "Ende nuk ka këngë të shkarkuara",
        downloadAction = "Shkarko",
        downloadedAction = "Shkarkuar",
        downloadingAction = "Duke shkarkuar...",
        removeDownloadAction = "Hiq shkarkimin",
    )
    AppLanguage.Bosnian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Preuzimanja",
        downloadsInProgress = "Preuzimanja u toku",
        noActiveDownloads = "Nema preuzimanja u toku",
        noDownloadedSongs = "Još nema preuzetih pjesama",
        downloadAction = "Preuzmi",
        downloadedAction = "Preuzeto",
        downloadingAction = "Preuzimanje...",
        removeDownloadAction = "Ukloni preuzimanje",
    )
    AppLanguage.Bulgarian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Изтегляния",
        downloadsInProgress = "Текущи изтегляния",
        noActiveDownloads = "Няма текущи изтегляния",
        noDownloadedSongs = "Все още няма изтеглени песни",
        downloadAction = "Изтегли",
        downloadedAction = "Изтеглено",
        downloadingAction = "Изтегляне...",
        removeDownloadAction = "Премахни изтеглянето",
    )
    AppLanguage.Catalan -> YouTubeDownloadUiCopy(
        downloadsTitle = "Baixades",
        downloadsInProgress = "Baixades en curs",
        noActiveDownloads = "No hi ha baixades en curs",
        noDownloadedSongs = "Encara no hi ha cançons baixades",
        downloadAction = "Baixa",
        downloadedAction = "Baixada",
        downloadingAction = "Baixant...",
        removeDownloadAction = "Elimina la baixada",
    )
    AppLanguage.Finnish -> YouTubeDownloadUiCopy(
        downloadsTitle = "Lataukset",
        downloadsInProgress = "Käynnissä olevat lataukset",
        noActiveDownloads = "Ei käynnissä olevia latauksia",
        noDownloadedSongs = "Ei vielä ladattuja kappaleita",
        downloadAction = "Lataa",
        downloadedAction = "Ladattu",
        downloadingAction = "Ladataan...",
        removeDownloadAction = "Poista lataus",
    )
    AppLanguage.Romanian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Descărcări",
        downloadsInProgress = "Descărcări în curs",
        noActiveDownloads = "Nicio descărcare în curs",
        noDownloadedSongs = "Nicio piesă descărcată încă",
        downloadAction = "Descarcă",
        downloadedAction = "Descărcată",
        downloadingAction = "Se descarcă...",
        removeDownloadAction = "Elimină descărcarea",
    )
    AppLanguage.Slovenian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Prenosi",
        downloadsInProgress = "Prenosi v teku",
        noActiveDownloads = "Ni prenosov v teku",
        noDownloadedSongs = "Še ni prenesenih skladb",
        downloadAction = "Prenesi",
        downloadedAction = "Preneseno",
        downloadingAction = "Prenašanje...",
        removeDownloadAction = "Odstrani prenos",
    )
    AppLanguage.Indonesian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Unduhan",
        downloadsInProgress = "Unduhan berlangsung",
        noActiveDownloads = "Tidak ada unduhan berlangsung",
        noDownloadedSongs = "Belum ada lagu yang diunduh",
        downloadAction = "Unduh",
        downloadedAction = "Diunduh",
        downloadingAction = "Mengunduh...",
        removeDownloadAction = "Hapus unduhan",
    )
    AppLanguage.Punjabi -> YouTubeDownloadUiCopy(
        downloadsTitle = "ਡਾਊਨਲੋਡ",
        downloadsInProgress = "ਚੱਲ ਰਹੇ ਡਾਊਨਲੋਡ",
        noActiveDownloads = "ਕੋਈ ਡਾਊਨਲੋਡ ਜਾਰੀ ਨਹੀਂ ਹੈ",
        noDownloadedSongs = "ਅਜੇ ਕੋਈ ਡਾਊਨਲੋਡ ਕੀਤਾ ਗੀਤ نہیں",
        downloadAction = "ਡਾਊਨਲੋਡ",
        downloadedAction = "ਡਾਊਨਲੋਡ ਕੀਤਾ",
        downloadingAction = "ਡਾਊਨਲੋਡ ਹੋ ਰਿਹਾ ਹੈ...",
        removeDownloadAction = "ਡਾਊਨਲੋਡ ਹਟਾਓ",
    )
    AppLanguage.Croatian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Preuzimanja",
        downloadsInProgress = "Preuzimanja u tijeku",
        noActiveDownloads = "Nema preuzimanja u tijeku",
        noDownloadedSongs = "Još nema preuzetih pjesama",
        downloadAction = "Preuzmi",
        downloadedAction = "Preuzeto",
        downloadingAction = "Preuzimanje...",
        removeDownloadAction = "Ukloni preuzimanje",
    )
    AppLanguage.Czech -> YouTubeDownloadUiCopy(
        downloadsTitle = "Stažené",
        downloadsInProgress = "Probíhající stahování",
        noActiveDownloads = "Žádné probíhající stahování",
        noDownloadedSongs = "Zatím žádné stažené skladby",
        downloadAction = "Stáhnout",
        downloadedAction = "Staženo",
        downloadingAction = "Stahování...",
        removeDownloadAction = "Odebrat stažení",
    )
    AppLanguage.Danish -> YouTubeDownloadUiCopy(
        downloadsTitle = "Downloads",
        downloadsInProgress = "Igangværende downloads",
        noActiveDownloads = "Ingen igangværende downloads",
        noDownloadedSongs = "Ingen downloadede sange endnu",
        downloadAction = "Download",
        downloadedAction = "Downloadet",
        downloadingAction = "Downloader...",
        removeDownloadAction = "Fjern download",
    )
    AppLanguage.Bengali -> YouTubeDownloadUiCopy(
        downloadsTitle = "ডাউনলোড",
        downloadsInProgress = "চলমান ডাউনলোড",
        noActiveDownloads = "কোনো ডাউনলোড চলছে না",
        noDownloadedSongs = "এখনো কোনো গান ডাউনলোড করা হয়নি",
        downloadAction = "ডাউনলোড",
        downloadedAction = "ডাউনলোড হয়েছে",
        downloadingAction = "ডাউনলোড হচ্ছে...",
        removeDownloadAction = "ডাউনলোড সরান",
    )
    AppLanguage.Estonian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Allalaadimised",
        downloadsInProgress = "Pooleliolevad allalaadimised",
        noActiveDownloads = "Pooleliolevaid allalaadimisi pole",
        noDownloadedSongs = "Allalaaditud lugusid veel pole",
        downloadAction = "Laadi alla",
        downloadedAction = "Alla laaditud",
        downloadingAction = "Allalaadimine...",
        removeDownloadAction = "Eemalda allalaadimine",
    )
    AppLanguage.Greek -> YouTubeDownloadUiCopy(
        downloadsTitle = "Λήψεις",
        downloadsInProgress = "Λήψεις σε εξέλιξη",
        noActiveDownloads = "Δεν υπάρχουν λήψεις σε εξέλιξη",
        noDownloadedSongs = "Δεν υπάρχουν ακόμη ληφθέντα τραγούδια",
        downloadAction = "Λήψη",
        downloadedAction = "Λήφθηκε",
        downloadingAction = "Λήψη...",
        removeDownloadAction = "Κατάργηση λήψης",
    )
    AppLanguage.Hindi -> YouTubeDownloadUiCopy(
        downloadsTitle = "डाउनलोड",
        downloadsInProgress = "जारी डाउनलोड",
        noActiveDownloads = "कोई डाउनलोड जारी नहीं है",
        noDownloadedSongs = "अभी कोई डाउनलोड किया गया गाना नहीं",
        downloadAction = "डाउनलोड",
        downloadedAction = "डाउनलोड किया गया",
        downloadingAction = "डाउनलोड हो रहा है...",
        removeDownloadAction = "डाउनलोड हटाएं",
    )
    AppLanguage.Hungarian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Letöltések",
        downloadsInProgress = "Folyamatban lévő letöltések",
        noActiveDownloads = "Nincs folyamatban lévő letöltés",
        noDownloadedSongs = "Még nincsenek letöltött dalok",
        downloadAction = "Letöltés",
        downloadedAction = "Letöltve",
        downloadingAction = "Letöltés...",
        removeDownloadAction = "Letöltés eltávolítása",
    )
    AppLanguage.Latvian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Lejupielādes",
        downloadsInProgress = "Notiekošās lejupielādes",
        noActiveDownloads = "Nav notiekošu lejupielāžu",
        noDownloadedSongs = "Vēl nav lejupielādētu dziesmu",
        downloadAction = "Lejupielādēt",
        downloadedAction = "Lejupielādēts",
        downloadingAction = "Lejupielādē...",
        removeDownloadAction = "Noņemt lejupielādi",
    )
    AppLanguage.Lithuanian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Atsisiuntimai",
        downloadsInProgress = "Vykstantys atsisiuntimai",
        noActiveDownloads = "Nėra vykstančių atsisiuntimų",
        noDownloadedSongs = "Dar nėra atsisiųstų dainų",
        downloadAction = "Atsisiųsti",
        downloadedAction = "Atsisiųsta",
        downloadingAction = "Atsisiunčiama...",
        removeDownloadAction = "Pašalinti atsisiuntimą",
    )
    AppLanguage.Malay -> YouTubeDownloadUiCopy(
        downloadsTitle = "Muat turun",
        downloadsInProgress = "Muat turun sedang berjalan",
        noActiveDownloads = "Tiada muat turun sedang berjalan",
        noDownloadedSongs = "Belum ada lagu dimuat turun",
        downloadAction = "Muat turun",
        downloadedAction = "Dimuat turun",
        downloadingAction = "Memuat turun...",
        removeDownloadAction = "Buang muat turun",
    )
    AppLanguage.Macedonian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Преземања",
        downloadsInProgress = "Преземања во тек",
        noActiveDownloads = "Нема преземања во тек",
        noDownloadedSongs = "Сè уште нема преземени песни",
        downloadAction = "Преземи",
        downloadedAction = "Преземено",
        downloadingAction = "Се презема...",
        removeDownloadAction = "Отстрани преземање",
    )
    AppLanguage.Norwegian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Nedlastinger",
        downloadsInProgress = "Pågående nedlastinger",
        noActiveDownloads = "Ingen pågående nedlastinger",
        noDownloadedSongs = "Ingen nedlastede sanger ennå",
        downloadAction = "Last ned",
        downloadedAction = "Lastet ned",
        downloadingAction = "Laster ned...",
        removeDownloadAction = "Fjern nedlasting",
    )
    AppLanguage.Slovak -> YouTubeDownloadUiCopy(
        downloadsTitle = "Stiahnuté",
        downloadsInProgress = "Prebiehajúce sťahovania",
        noActiveDownloads = "Žiadne prebiehajúce sťahovania",
        noDownloadedSongs = "Zatiaľ žiadne stiahnuté skladby",
        downloadAction = "Stiahnuť",
        downloadedAction = "Stiahnuté",
        downloadingAction = "Sťahuje sa...",
        removeDownloadAction = "Odstrániť stiahnuté",
    )
    AppLanguage.Serbian -> YouTubeDownloadUiCopy(
        downloadsTitle = "Преузимања",
        downloadsInProgress = "Преузимања у току",
        noActiveDownloads = "Нема преузимања у току",
        noDownloadedSongs = "Још нема преузетих песама",
        downloadAction = "Преузми",
        downloadedAction = "Преузето",
        downloadingAction = "Преузимање...",
        removeDownloadAction = "Уклони преузимање",
    )
    AppLanguage.Swedish -> YouTubeDownloadUiCopy(
        downloadsTitle = "Nedladdningar",
        downloadsInProgress = "Pågående nedladdningar",
        noActiveDownloads = "Inga pågående nedladdningar",
        noDownloadedSongs = "Inga nedladdade låtar än",
        downloadAction = "Ladda ner",
        downloadedAction = "Nedladdad",
        downloadingAction = "Laddar ner...",
        removeDownloadAction = "Ta bort nedladdning",
    )
    AppLanguage.Thai -> YouTubeDownloadUiCopy(
        downloadsTitle = "ดาวน์โหลด",
        downloadsInProgress = "กำลังดาวน์โหลด",
        noActiveDownloads = "ไม่มีการดาวน์โหลดที่กำลังทำงาน",
        noDownloadedSongs = "ยังไม่มีเพลงที่ดาวน์โหลด",
        downloadAction = "ดาวน์โหลด",
        downloadedAction = "ดาวน์โหลดแล้ว",
        downloadingAction = "กำลังดาวน์โหลด...",
        removeDownloadAction = "ลบการดาวน์โหลด",
    )
    AppLanguage.Urdu -> YouTubeDownloadUiCopy(
        downloadsTitle = "ڈاؤن لوڈز",
        downloadsInProgress = "جاری ڈاؤن لوڈز",
        noActiveDownloads = "کوئی ڈاؤن لوڈ جاری نہیں ہے",
        noDownloadedSongs = "ابھی تک کوئی ڈاؤن لوڈ گانا نہیں",
        downloadAction = "ڈاؤن لوڈ",
        downloadedAction = "ڈاؤن لوڈ شدہ",
        downloadingAction = "ڈاؤن لوڈ ہو رہا ہے...",
        removeDownloadAction = "ڈاؤن لوڈ ہٹائیں",
    )
}
