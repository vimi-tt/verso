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

internal data class PlaylistEditActionUiCopy(
    val editDescription: String,
    val descriptionLabel: String,
    val changeImage: String,
    val deletePlaylist: String,
    val deletePlaylistConfirm: String,
    val addToYouTube: String,
    val addingToYouTube: String,
    val addedToYouTube: String,
    val exportToYouTubeFailed: String,
    val moveUpCd: String,
    val moveDownCd: String,
    val save: String,
    val cancel: String,
    val delete: String,
    val cropCoverTitle: String = "Crop cover",
    val cropCoverHint: String = "Drag and pinch to fit the square cover",
)

@Suppress("CyclomaticComplexMethod")
internal fun playlistEditActionCopy(language: AppLanguage): PlaylistEditActionUiCopy = when (language) {
    AppLanguage.English -> PlaylistEditActionUiCopy("Edit description", "Description", "Change image", "Delete playlist", "Delete this YouTube playlist?", "Add to YouTube", "Adding to YouTube...", "Added to YouTube", "Unable to add to YouTube", "Move up", "Move down", "Save", "Cancel", "Delete")
    AppLanguage.Portuguese -> PlaylistEditActionUiCopy("Editar descrição", "Descrição", "Alterar imagem", "Apagar playlist", "Apagar esta playlist do YouTube?", "Adicionar ao YouTube", "Adicionando ao YouTube...", "Adicionada ao YouTube", "Não foi possível adicionar ao YouTube", "Mover para cima", "Mover para baixo", "Salvar", "Cancelar", "Apagar")
    AppLanguage.Spanish -> PlaylistEditActionUiCopy("Editar descripción", "Descripción", "Cambiar imagen", "Eliminar playlist", "¿Eliminar esta playlist de YouTube?", "Añadir a YouTube", "Añadiendo a YouTube...", "Añadida a YouTube", "No se pudo añadir a YouTube", "Subir", "Bajar", "Guardar", "Cancelar", "Eliminar")
    AppLanguage.French -> PlaylistEditActionUiCopy("Modifier la description", "Description", "Changer l'image", "Supprimer la playlist", "Supprimer cette playlist YouTube ?", "Ajouter à YouTube", "Ajout à YouTube...", "Ajoutée à YouTube", "Impossible d'ajouter à YouTube", "Monter", "Descendre", "Enregistrer", "Annuler", "Supprimer")
    AppLanguage.German -> PlaylistEditActionUiCopy("Beschreibung bearbeiten", "Beschreibung", "Bild ändern", "Playlist löschen", "Diese YouTube-Playlist löschen?", "Zu YouTube hinzufügen", "Wird zu YouTube hinzugefügt...", "Zu YouTube hinzugefügt", "Hinzufügen zu YouTube fehlgeschlagen", "Nach oben", "Nach unten", "Speichern", "Abbrechen", "Löschen")
    AppLanguage.Italian -> PlaylistEditActionUiCopy("Modifica descrizione", "Descrizione", "Cambia immagine", "Elimina playlist", "Eliminare questa playlist di YouTube?", "Aggiungi a YouTube", "Aggiunta a YouTube...", "Aggiunta a YouTube", "Impossibile aggiungere a YouTube", "Sposta su", "Sposta giù", "Salva", "Annulla", "Elimina")
    AppLanguage.Dutch -> PlaylistEditActionUiCopy("Beschrijving bewerken", "Beschrijving", "Afbeelding wijzigen", "Afspeellijst verwijderen", "Deze YouTube-afspeellijst verwijderen?", "Toevoegen aan YouTube", "Toevoegen aan YouTube...", "Toegevoegd aan YouTube", "Toevoegen aan YouTube mislukt", "Omhoog", "Omlaag", "Opslaan", "Annuleren", "Verwijderen")
    AppLanguage.Polish -> PlaylistEditActionUiCopy("Edytuj opis", "Opis", "Zmień obraz", "Usuń playlistę", "Usunąć tę playlistę YouTube?", "Dodaj do YouTube", "Dodawanie do YouTube...", "Dodano do YouTube", "Nie udało się dodać do YouTube", "W górę", "W dół", "Zapisz", "Anuluj", "Usuń")
    AppLanguage.Russian -> PlaylistEditActionUiCopy("Изменить описание", "Описание", "Сменить обложку", "Удалить плейлист", "Удалить этот плейлист YouTube?", "Добавить в YouTube", "Добавление в YouTube...", "Добавлено в YouTube", "Не удалось добавить в YouTube", "Вверх", "Вниз", "Сохранить", "Отмена", "Удалить")
    AppLanguage.Ukrainian -> PlaylistEditActionUiCopy("Змінити опис", "Опис", "Змінити обкладинку", "Видалити плейлист", "Видалити цей плейлист YouTube?", "Додати до YouTube", "Додавання до YouTube...", "Додано до YouTube", "Не вдалося додати до YouTube", "Вгору", "Вниз", "Зберегти", "Скасувати", "Видалити")
    AppLanguage.Japanese -> PlaylistEditActionUiCopy("説明を編集", "説明", "画像を変更", "プレイリストを削除", "このYouTubeプレイリストを削除しますか？", "YouTubeに追加", "YouTubeに追加中...", "YouTubeに追加しました", "YouTubeに追加できませんでした", "上へ移動", "下へ移動", "保存", "キャンセル", "削除")
    AppLanguage.Korean -> PlaylistEditActionUiCopy("설명 편집", "설명", "이미지 변경", "재생목록 삭제", "이 YouTube 재생목록을 삭제할까요?", "YouTube에 추가", "YouTube에 추가 중...", "YouTube에 추가됨", "YouTube에 추가할 수 없습니다", "위로 이동", "아래로 이동", "저장", "취소", "삭제")
    AppLanguage.ChineseSimplified -> PlaylistEditActionUiCopy("编辑简介", "简介", "更换封面", "删除播放列表", "删除此 YouTube 播放列表？", "添加到 YouTube", "正在添加到 YouTube...", "已添加到 YouTube", "无法添加到 YouTube", "上移", "下移", "保存", "取消", "删除")
    AppLanguage.Hindi -> PlaylistEditActionUiCopy("विवरण संपादित करें", "विवरण", "चित्र बदलें", "प्लेलिस्ट हटाएं", "क्या यह YouTube प्लेलिस्ट हटाएं?", "YouTube में जोड़ें", "YouTube में जोड़ा जा रहा है...", "YouTube में जोड़ा गया", "YouTube में जोड़ना विफल रहा", "ऊपर ले जाएं", "नीचे ले जाएं", "सहेजें", "रद्द करें", "हटाएं")
    AppLanguage.Bengali -> PlaylistEditActionUiCopy("বিবরণ সম্পাদনা", "বিবরণ", "ছবি পরিবর্তন করুন", "প্লেলিস্ট মুছুন", "এই YouTube প্লেলিস্টটি মুছবেন?", "YouTube-এ যোগ করুন", "YouTube-এ যোগ করা হচ্ছে...", "YouTube-এ যোগ হয়েছে", "YouTube-এ যোগ করা যায়নি", "উপরে সরান", "নিচে সরান", "সংরক্ষণ", "বাতিল", "মুছুন")
    AppLanguage.Punjabi -> PlaylistEditActionUiCopy("ਵੇਰਵਾ ਸੋਧੋ", "ਵੇਰਵਾ", "ਤਸਵੀਰ ਬਦਲੋ", "ਪਲੇਲਿਸਟ ਮਿਟਾਓ", "ਕੀ ਇਹ YouTube ਪਲੇਲਿਸਟ ਮਿਟਾਉਣੀ ਹੈ?", "YouTube ਵਿੱਚ ਸ਼ਾਮਲ ਕਰੋ", "YouTube ਵਿੱਚ ਸ਼ਾਮਲ ਕੀਤਾ ਜਾ ਰਿਹਾ ਹੈ...", "YouTube ਵਿੱਚ ਸ਼ਾਮਲ ਕੀਤਾ ਗਿਆ", "YouTube ਵਿੱਚ ਸ਼ਾਮਲ ਨਹੀਂ ਹੋ ਸਕਿਆ", "ਉੱਪਰ ਲਿਜਾਓ", "ਹੇਠਾਂ ਲਿਜਾਓ", "ਸੰਭਾਲੋ", "ਰੱਦ ਕਰੋ", "ਮਿਟਾਓ")
    AppLanguage.Urdu -> PlaylistEditActionUiCopy("تفصیل میں ترمیم کریں", "تفصیل", "تصویر تبدیل کریں", "پلے لسٹ حذف کریں", "کیا یہ YouTube پلے لسٹ حذف کریں؟", "YouTube میں شامل کریں", "YouTube میں شامل ہو رہا ہے...", "YouTube میں شامل ہو گئی", "YouTube میں شامل نہ ہو سکی", "اوپر لے جائیں", "نیچے لے جائیں", "محفوظ کریں", "منسوخ کریں", "حذف کریں")
    AppLanguage.Indonesian -> PlaylistEditActionUiCopy("Edit deskripsi", "Deskripsi", "Ubah gambar", "Hapus playlist", "Hapus playlist YouTube ini?", "Tambahkan ke YouTube", "Menambahkan ke YouTube...", "Ditambahkan ke YouTube", "Tidak dapat menambahkan ke YouTube", "Naikkan", "Turunkan", "Simpan", "Batal", "Hapus")
    AppLanguage.Malay -> PlaylistEditActionUiCopy("Edit penerangan", "Penerangan", "Tukar gambar", "Padam senarai main", "Padam senarai main YouTube ini?", "Tambah ke YouTube", "Menambah ke YouTube...", "Ditambah ke YouTube", "Tidak dapat menambah ke YouTube", "Naikkan", "Turunkan", "Simpan", "Batal", "Padam")
    AppLanguage.Thai -> PlaylistEditActionUiCopy("แก้ไขคำอธิบาย", "คำอธิบาย", "เปลี่ยนรูปภาพ", "ลบเพลย์ลิสต์", "ลบเพลย์ลิสต์ YouTube นี้หรือไม่?", "เพิ่มไปยัง YouTube", "กำลังเพิ่มไปยัง YouTube...", "เพิ่มไปยัง YouTube แล้ว", "ไม่สามารถเพิ่มไปยัง YouTube", "เลื่อนขึ้น", "เลื่อนลง", "บันทึก", "ยกเลิก", "ลบ")
    AppLanguage.Albanian -> PlaylistEditActionUiCopy("Ndrysho përshkrimin", "Përshkrimi", "Ndrysho imazhin", "Fshi listën", "Të fshihet kjo listë YouTube?", "Shto në YouTube", "Duke shtuar në YouTube...", "U shtua në YouTube", "Nuk u shtua në YouTube", "Lëviz lart", "Lëviz poshtë", "Ruaj", "Anulo", "Fshi")
    AppLanguage.Bosnian -> PlaylistEditActionUiCopy("Uredi opis", "Opis", "Promijeni sliku", "Obriši plejlistu", "Obrisati ovu YouTube plejlistu?", "Dodaj na YouTube", "Dodavanje na YouTube...", "Dodano na YouTube", "Nije moguće dodati na YouTube", "Pomjeri gore", "Pomjeri dolje", "Sačuvaj", "Otkaži", "Obriši")
    AppLanguage.Bulgarian -> PlaylistEditActionUiCopy("Редактирай описанието", "Описание", "Смени изображението", "Изтрий плейлиста", "Изтриване на този YouTube плейлист?", "Добави в YouTube", "Добавяне в YouTube...", "Добавено в YouTube", "Неуспешно добавяне в YouTube", "Нагоре", "Надолу", "Запази", "Отказ", "Изтрий")
    AppLanguage.Catalan -> PlaylistEditActionUiCopy("Edita la descripció", "Descripció", "Canvia la imatge", "Suprimeix la llista", "Vols suprimir aquesta llista de YouTube?", "Afegeix a YouTube", "Afegint a YouTube...", "Afegida a YouTube", "No s'ha pogut afegir a YouTube", "Mou amunt", "Mou avall", "Desa", "Cancel·la", "Suprimeix")
    AppLanguage.Croatian -> PlaylistEditActionUiCopy("Uredi opis", "Opis", "Promijeni sliku", "Izbriši popis", "Izbrisati ovaj YouTube popis?", "Dodaj na YouTube", "Dodavanje na YouTube...", "Dodano na YouTube", "Nije moguće dodati na YouTube", "Pomakni gore", "Pomakni dolje", "Spremi", "Odustani", "Izbriši")
    AppLanguage.Czech -> PlaylistEditActionUiCopy("Upravit popis", "Popis", "Změnit obrázek", "Smazat playlist", "Smazat tento YouTube playlist?", "Přidat na YouTube", "Přidávání na YouTube...", "Přidáno na YouTube", "Nepodařilo se přidat na YouTube", "Posunout nahoru", "Posunout dolů", "Uložit", "Zrušit", "Smazat")
    AppLanguage.Danish -> PlaylistEditActionUiCopy("Rediger beskrivelse", "Beskrivelse", "Skift billede", "Slet playliste", "Slet denne YouTube-playliste?", "Tilføj til YouTube", "Tilføjer til YouTube...", "Tilføjet til YouTube", "Kunne ikke tilføje til YouTube", "Flyt op", "Flyt ned", "Gem", "Annuller", "Slet")
    AppLanguage.Estonian -> PlaylistEditActionUiCopy("Muuda kirjeldust", "Kirjeldus", "Muuda pilti", "Kustuta esitusloend", "Kustutada see YouTube'i esitusloend?", "Lisa YouTube'i", "Lisamine YouTube'i...", "Lisatud YouTube'i", "YouTube'i lisamine ebaõnnestus", "Liiguta üles", "Liiguta alla", "Salvesta", "Tühista", "Kustuta")
    AppLanguage.Finnish -> PlaylistEditActionUiCopy("Muokkaa kuvausta", "Kuvaus", "Vaihda kuva", "Poista soittolista", "Poistetaanko tämä YouTube-soittolista?", "Lisää YouTubeen", "Lisätään YouTubeen...", "Lisätty YouTubeen", "Lisääminen YouTubeen epäonnistui", "Siirrä ylös", "Siirrä alas", "Tallenna", "Peruuta", "Poista")
    AppLanguage.Greek -> PlaylistEditActionUiCopy("Επεξεργασία περιγραφής", "Περιγραφή", "Αλλαγή εικόνας", "Διαγραφή λίστας", "Διαγραφή αυτής της λίστας YouTube;", "Προσθήκη στο YouTube", "Προσθήκη στο YouTube...", "Προστέθηκε στο YouTube", "Αποτυχία προσθήκης στο YouTube", "Μετακίνηση πάνω", "Μετακίνηση κάτω", "Αποθήκευση", "Ακύρωση", "Διαγραφή")
    AppLanguage.Hungarian -> PlaylistEditActionUiCopy("Leírás szerkesztése", "Leírás", "Kép módosítása", "Lejátszási lista törlése", "Törli ezt a YouTube-lejátszási listát?", "Hozzáadás a YouTube-hoz", "Hozzáadás a YouTube-hoz...", "Hozzáadva a YouTube-hoz", "Nem sikerült hozzáadni a YouTube-hoz", "Feljebb", "Lejjebb", "Mentés", "Mégse", "Törlés")
    AppLanguage.Latvian -> PlaylistEditActionUiCopy("Rediģēt aprakstu", "Apraksts", "Mainīt attēlu", "Dzēst sarakstu", "Dzēst šo YouTube sarakstu?", "Pievienot YouTube", "Pievieno YouTube...", "Pievienots YouTube", "Neizdevās pievienot YouTube", "Pārvietot augšup", "Pārvietot lejup", "Saglabāt", "Atcelt", "Dzēst")
    AppLanguage.Lithuanian -> PlaylistEditActionUiCopy("Redaguoti aprašą", "Aprašas", "Keisti paveikslėlį", "Ištrinti grojaraštį", "Ištrinti šį YouTube grojaraštį?", "Pridėti į YouTube", "Pridedama į YouTube...", "Pridėta į YouTube", "Nepavyko pridėti į YouTube", "Perkelti aukštyn", "Perkelti žemyn", "Išsaugoti", "Atšaukti", "Ištrinti")
    AppLanguage.Macedonian -> PlaylistEditActionUiCopy("Уреди опис", "Опис", "Промени слика", "Избриши плејлиста", "Да се избрише оваа YouTube плејлиста?", "Додај на YouTube", "Се додава на YouTube...", "Додадено на YouTube", "Не може да се додаде на YouTube", "Премести горе", "Премести долу", "Зачувај", "Откажи", "Избриши")
    AppLanguage.Norwegian -> PlaylistEditActionUiCopy("Rediger beskrivelse", "Beskrivelse", "Endre bilde", "Slett spilleliste", "Slette denne YouTube-spillelisten?", "Legg til på YouTube", "Legger til på YouTube...", "Lagt til på YouTube", "Kunne ikke legge til på YouTube", "Flytt opp", "Flytt ned", "Lagre", "Avbryt", "Slett")
    AppLanguage.Romanian -> PlaylistEditActionUiCopy("Editează descrierea", "Descriere", "Schimbă imaginea", "Șterge playlistul", "Ștergi acest playlist YouTube?", "Adaugă pe YouTube", "Se adaugă pe YouTube...", "Adăugat pe YouTube", "Nu s-a putut adăuga pe YouTube", "Mută sus", "Mută jos", "Salvează", "Anulează", "Șterge")
    AppLanguage.Serbian -> PlaylistEditActionUiCopy("Уреди опис", "Опис", "Промени слику", "Обриши плејлисту", "Обрисати ову YouTube плејлисту?", "Додај на YouTube", "Додавање на YouTube...", "Додато на YouTube", "Није могуће додати на YouTube", "Помери горе", "Помери доле", "Сачувај", "Откажи", "Обриши")
    AppLanguage.Slovak -> PlaylistEditActionUiCopy("Upraviť popis", "Popis", "Zmeniť obrázok", "Odstrániť playlist", "Odstrániť tento YouTube playlist?", "Pridať na YouTube", "Pridáva sa na YouTube...", "Pridané na YouTube", "Nepodarilo sa pridať na YouTube", "Posunúť hore", "Posunúť dole", "Uložiť", "Zrušiť", "Odstrániť")
    AppLanguage.Slovenian -> PlaylistEditActionUiCopy("Uredi opis", "Opis", "Spremeni sliko", "Izbriši seznam", "Izbrisati ta seznam YouTube?", "Dodaj v YouTube", "Dodajanje v YouTube...", "Dodano v YouTube", "Ni mogoče dodati v YouTube", "Premakni gor", "Premakni dol", "Shrani", "Prekliči", "Izbriši")
    AppLanguage.Swedish -> PlaylistEditActionUiCopy("Redigera beskrivning", "Beskrivning", "Byt bild", "Radera spellista", "Radera denna YouTube-spellista?", "Lägg till på YouTube", "Lägger till på YouTube...", "Tillagd på YouTube", "Kunde inte lägga till på YouTube", "Flytta upp", "Flytta ner", "Spara", "Avbryt", "Radera")
}

@Suppress("CyclomaticComplexMethod")
internal fun cropCoverCopy(language: AppLanguage): Pair<String, String> = when (language) {
    AppLanguage.English -> "Crop cover" to "Drag and pinch to fit the square cover"
    AppLanguage.Portuguese -> "Recortar capa" to "Arraste e aproxime para ajustar ao quadrado"
    AppLanguage.Spanish -> "Recortar portada" to "Arrastra y pellizca para ajustar al cuadrado"
    AppLanguage.French -> "Recadrer la pochette" to "Faites glisser et pincez pour ajuster au carré"
    AppLanguage.German -> "Cover zuschneiden" to "Ziehen und zoomen zum Anpassen ans Quadrat"
    AppLanguage.Italian -> "Ritaglia copertina" to "Trascina e pizzica per adattare al quadrato"
    AppLanguage.Dutch -> "Omslag bijsnijden" to "Sleep en knijp om in het vierkant te passen"
    AppLanguage.Polish -> "Przytnij okładkę" to "Przeciągnij i powiększ, aby dopasować do kwadratu"
    AppLanguage.Russian -> "Обрезать обложку" to "Перетащите и масштабируйте под квадрат"
    AppLanguage.Ukrainian -> "Обрізати обкладинку" to "Перетягніть і масштабуйте під квадрат"
    AppLanguage.Japanese -> "カバーを切り抜く" to "ドラッグとピンチで正方形に合わせます"
    AppLanguage.Korean -> "커버 자르기" to "드래그 및 확대/축소하여 정사각형에 맞추세요"
    AppLanguage.ChineseSimplified -> "裁剪封面" to "拖动和双指缩放以适应方形封面"
    AppLanguage.Hindi -> "कवर क्रॉप करें" to "वर्गाकार कवर में फ़िट करने के लिए खींचें और पिंच करें"
    AppLanguage.Bengali -> "কভার ক্রপ করুন" to "বর্গাকার কভারে ফিট করতে টানুন ও পিঞ্চ করুন"
    AppLanguage.Punjabi -> "ਕਵਰ ਕੱਟੋ" to "ਵਰਗਾਕਾਰ ਕਵਰ ਵਿੱਚ ਫਿੱਟ ਕਰਨ ਲਈ ਖਿੱਚੋ ਅਤੇ ਪਿੰਚ ਕਰੋ"
    AppLanguage.Urdu -> "کور کاٹیں" to "مربع کور میں فٹ کرنے کے لیے گھسیٹیں اور پنچ کریں"
    AppLanguage.Indonesian -> "Pangkas sampul" to "Seret dan cubit agar pas dengan kotak"
    AppLanguage.Malay -> "Pangkas muka depan" to "Seret dan cubit untuk muat segi empat"
    AppLanguage.Thai -> "ครอบตัดปก" to "ลากและบีบนิ้วเพื่อปรับให้พอดีกับสี่เหลี่ยมจัตุรัส"
    AppLanguage.Albanian -> "Prit kapakun" to "Zvarrit dhe zmadho për ta përshtatur në katror"
    AppLanguage.Bosnian -> "Izreži omot" to "Prevucite i zumirajte da stane u kvadrat"
    AppLanguage.Bulgarian -> "Изрежи обложката" to "Плъзнете и мащабирайте в квадрата"
    AppLanguage.Catalan -> "Retalla la portada" to "Arrossega i pessiga per ajustar al quadrat"
    AppLanguage.Croatian -> "Izreži omot" to "Povucite i zumirajte za kvadratni okvir"
    AppLanguage.Czech -> "Oříznout obal" to "Tažením a sevřením přizpůsobte čtverci"
    AppLanguage.Danish -> "Beskær cover" to "Træk og knib for at tilpasse kvadratet"
    AppLanguage.Estonian -> "Kärbi kaant" to "Lohista ja suumi ruutu sobitamiseks"
    AppLanguage.Finnish -> "Rajaa kansikuva" to "Vedä ja nipistä sovittaaksesi neliöön"
    AppLanguage.Greek -> "Περικοπή εξωφύλλου" to "Σύρετε και τσιμπήστε για προσαρμογή στο τετράγωνο"
    AppLanguage.Hungarian -> "Borító vágása" to "Húzza és nagyítsa a négyzethez igazításhoz"
    AppLanguage.Latvian -> "Apgriezt vāku" to "Velciet un tuviniet, lai pielāgotu kvadrātam"
    AppLanguage.Lithuanian -> "Apkarpyti viršelį" to "Vilkite ir priartinkite prie kvadrato"
    AppLanguage.Macedonian -> "Исечи обвивка" to "Повлечете и зумирајте за да одговара на квадратот"
    AppLanguage.Norwegian -> "Beskjær omslag" to "Dra og klyp for å tilpasse kvadratet"
    AppLanguage.Romanian -> "Decupează coperta" to "Trage și ciupește pentru a potrivi în pătrat"
    AppLanguage.Serbian -> "Исеци омот" to "Превуците и зумирајте да стане у квадрат"
    AppLanguage.Slovak -> "Orezať obal" to "Potiahnutím a zovretím prispôsobte štvorcu"
    AppLanguage.Slovenian -> "Obreži naslovnico" to "Povlecite in približajte za kvadrat"
    AppLanguage.Swedish -> "Beskär omslag" to "Dra och nyp för att passa kvadraten"
}
