package com.lostf1sh.pixelplayeross.presentation.utils

import com.lostf1sh.pixelplayeross.R

object GenreIconProvider {

    val DEFAULT_GENRES = listOf(
        "Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip Hop",
        "Country", "Blues", "Reggae", "Metal", "Folk", "R&B", "Punk", "Indie",
        "Alternative", "Latino", "Reggaeton", "Salsa", "Bachata", "Merengue", "Cumbia",
        "Oldies", "Soundtrack", "Gaming", "Sleep", "Workout", "Party", "Focus",
        "Gospel", "Children's", "World", "Dance", "New Age", "Easy Listening",
        "Afrobeats", "Synthwave", "Drum and Bass", "Lo-fi", "Phonk", "Anime",
        "Balada", "Sertanejo", "Forró", "Tango", "Norteño", "Música Tropical",
        "Schlager", "Chanson", "Enka", "Trot"
    )

    val SELECTABLE_ICONS = listOf(
        R.drawable.rock, R.drawable.pop_mic, R.drawable.sax, R.drawable.clasic_piano,
        R.drawable.electronic_sound, R.drawable.rapper, R.drawable.banjo, R.drawable.harmonica,
        R.drawable.maracas, R.drawable.metal_guitar, R.drawable.metal_guitar_2, R.drawable.accordion,
        R.drawable.synth_piano, R.drawable.punk, R.drawable.idk_indie_ig, R.drawable.acoustic_guitar,
        R.drawable.alt_video, R.drawable.star_angle, R.drawable.conga, R.drawable.bongos,
        R.drawable.drum, R.drawable.rattle, R.drawable.rounded_schedule_24, R.drawable.rounded_tv_24,
        R.drawable.rounded_touch_app_24, R.drawable.rounded_alarm_24, R.drawable.rounded_celebration_24,
        R.drawable.rounded_edit_24, R.drawable.rounded_favorite_24, R.drawable.rounded_lyrics_24,
        R.drawable.rounded_library_music_24, R.drawable.rounded_music_note_24,
        R.drawable.rounded_headphones_24, R.drawable.rounded_speaker_24
    )

    @Suppress("CyclomaticComplexMethod")
    fun getGenreImageResource(genreId: String, customIcons: Map<String, Int> = emptyMap()): Any {
        customIcons[genreId]?.let { return it }

        return when (genreId.lowercase().trim()) {

            // ── ROCK ─────────────────────────────────────────────────────────────
            "rock", "hard rock", "classic rock", "southern rock", "progressive rock",
            "prog rock", "progressive", "math rock", "post-rock", "post rock",
            "soft rock", "j-rock", "j rock", "art rock", "symphonic rock", "space rock",
            "psychedelic rock", "glam rock", "garage rock", "country rock", "slow rock",
            "post-grunge", "folk rock", "folk-rock", "swamp rock", "power pop rock",
            "rock and roll", "rock & roll",
            // slash/conjunction variants
            "rock/pop", "rock/metal", "rock/punk", "rock music",
            // ES
            "rock clásico", "rock clasico", "rock duro", "rock progresivo",
            "rock suave", "rock alternativo", "rock en español", "rock en espanol",
            // PT
            "rock brasileiro", "rock nacional",
            // FR
            "rock français", "rock alternatif",
            // DE
            "krautrock",
            // IT
            "rock italiano",
            // JA
            "ロック",
            // KO
            "록",
            // ZH
            "摇滚", "摇滚乐", "搖滾", "搖滾樂", "民谣摇滚" -> R.drawable.rock

            // ── ALTERNATIVE ──────────────────────────────────────────────────────
            "alternative", "alt-rock", "alternative rock", "experimental",
            "avant-garde", "avantgarde", "abstract", "psychedelic", "psychadelic",
            "neo-psychedelia", "art rock alt",
            // slash/conjunction variants
            "alternative/indie", "indie/alternative",
            // ES
            "alternativo", "alternativa", "rock alternativo independiente",
            // PT
            "alternativo brasileiro",
            // FR
            "alternatif",
            // DE
            "alternativ",
            // IT
            "alternativa",
            // ZH
            "另类", "另類" -> R.drawable.alt_video

            // ── METAL ─────────────────────────────────────────────────────────────
            "metal", "heavy metal", "death metal", "black metal", "thrash metal",
            "speed metal", "power metal", "doom metal", "stoner rock", "stoner metal",
            "sludge", "sludge metal", "gothic metal", "symphonic metal", "folk metal",
            "pagan metal", "viking metal", "glam metal",
            "metal music",
            // ES
            "metal pesado", "metal extremo",
            // JA
            "メタル", "ヘヴィメタル", "ヘビーメタル",
            // KO
            "메탈", "헤비메탈",
            // ZH
            "金属", "重金属", "死亡金属", "金屬", "重金屬" -> R.drawable.metal_guitar

            "nu metal", "nu-metal", "metalcore", "deathcore", "screamo",
            "noise rock", "industrial", "noise", "grindcore", "djent",
            "mathcore", "technical death metal", "brutal death metal",
            "melodic death metal", "progressive death metal",
            "blackgaze", "ebm", "industrial rock", "post-metal",
            "terror", "frenchcore" -> R.drawable.metal_guitar_2

            // ── PUNK ──────────────────────────────────────────────────────────────
            "punk", "punk rock", "pop punk", "grunge", "emo", "post-punk",
            "post punk", "hardcore punk", "street punk", "skate punk",
            "melodic punk", "melodic hardcore", "acid punk", "folk punk",
            "garage punk", "anarcho-punk", "crust punk", "emo pop",
            "punk music",
            // slash/conjunction
            "punk/rock", "rock/punk",
            // ES
            "punk en español",
            // JA
            "パンク", "パンクロック",
            // KO
            "펑크",
            // ZH
            "朋克" -> R.drawable.punk

            // ── INDIE ─────────────────────────────────────────────────────────────
            "indie", "indie rock", "indie pop", "lo-fi", "lo fi",
            "shoegaze", "dream pop", "noise pop", "twee pop", "sadcore", "slowcore",
            "britpop", "brit pop",
            "indie music",
            // ES
            "indie en español", "indie latinoamericano",
            // JA
            "インディー", "インディ",
            // KO
            "인디", "인디 음악",
            // ZH
            "独立", "独立音乐", "獨立", "獨立音樂" -> R.drawable.idk_indie_ig

            // ── POP ───────────────────────────────────────────────────────────────
            "pop", "pop rock", "k-pop", "dance pop", "teen pop", "bubblegum pop",
            "adult contemporary", "j-pop", "c-pop", "mandopop", "cantopop",
            "dance-pop", "europop", "karaoke", "power pop", "art pop",
            "vocal", "top 40", "eurodance",
            "pop music", "pop/dance", "dance/pop", "pop/rock",
            // slash/conjunction variants
            "pop/latin", "latin/pop",
            // ES
            "pop latino", "pop en español", "pop en espanol", "musica pop",
            "música pop", "pop español", "pop espanol", "contemporaneo", "contemporáneo",
            // PT
            "mpb", "música popular brasileira", "musica popular brasileira",
            "brega", "arrocha", "tropicália", "tropicalia", "tropicalismo",
            "opm",
            // FR
            "chanson", "chanson française", "chanson francaise",
            "variété", "variete", "variété française", "variete francaise",
            // DE
            "schlager",
            // IT
            "canzone italiana", "musica italiana", "cantautori", "cantautore",
            "musica napoletana", "napoletana",
            // Asia
            "t-pop", "v-pop",
            // JA
            "ポップス", "ポップ", "Jポップ",
            // KO
            "팝", "k-팝", "k팝", "트로트",
            // ZH
            "流行", "流行音乐", "流行音樂", "华语流行", "粤语", "粤語", "粤语流行",
            "粤语歌", "台语", "国语", "國語" -> R.drawable.pop_mic

            // ── SYNTH-POP / NEW WAVE / DARKWAVE ──────────────────────────────────
            "synth-pop", "synthpop", "new wave", "electropop",
            "synthwave", "outrun", "retrowave", "vaporwave",
            "darkwave", "coldwave", "electroclash",
            // ES
            "onda retro", "nueva ola",
            // DE
            "neue deutsche welle", "ndw",
            // ZH
            "合成器流行" -> R.drawable.synth_piano

            // ── HIP HOP / RAP ─────────────────────────────────────────────────────
            "hip hop", "hip-hop", "rap", "trap", "gangsta rap", "reggaeton",
            "lo-fi hip hop", "lofi hip hop", "lo fi hip hop", "chillhop",
            "phonk", "drill", "cloud rap", "mumble rap", "trip-hop", "trip hop",
            "g-funk", "gangsta", "freestyle", "christian rap", "christian gangsta rap",
            "hip hop music", "hip-hop music", "rap music",
            // slash/conjunction variants
            "rap/hip-hop", "hip-hop/rap", "hip hop/rap", "trap/hip-hop",
            "hip-hop/trap",
            // ES
            "hip hop en español", "rap en español", "rap en espanol",
            "rap español", "rap espanol", "trap en español", "trap en espanol",
            "corridos tumbados", "trap latino", "urbano",
            // PT
            "funk carioca", "funk brasileiro", "brega funk",
            // FR
            "rap français", "rap francais",
            // JA
            "ヒップホップ", "ラップ",
            // KO
            "힙합", "랩",
            // ZH
            "嘻哈", "说唱", "說唱" -> R.drawable.rapper

            // ── JAZZ ──────────────────────────────────────────────────────────────
            "jazz", "smooth jazz", "bebop", "swing", "big band", "dixieland",
            "jazz fusion", "fusion", "cool jazz", "free jazz", "latin jazz",
            "acid jazz", "nu jazz", "spiritual jazz", "electro swing", "swing jazz",
            "fast fusion", "jazz+funk", "jazz blues",
            "jazz music",
            // slash/conjunction variants
            "jazz/blues", "blues/jazz",
            // PT
            "choro", "chorinho",
            // JA
            "ジャズ",
            // KO
            "재즈",
            // ZH
            "爵士", "爵士乐", "爵士樂", "爵士蓝调" -> R.drawable.sax

            // ── BLUES ─────────────────────────────────────────────────────────────
            "blues", "rhythm & blues", "delta blues", "chicago blues",
            "electric blues", "boogie", "boogie-woogie",
            "blues music",
            // JA
            "ブルース",
            // KO
            "블루스",
            // ZH
            "蓝调", "藍調" -> R.drawable.harmonica

            // ── CLASSICAL ─────────────────────────────────────────────────────────
            "classical", "orchestra", "symphony", "piano", "baroque", "opera",
            "chamber", "chamber music", "choral", "contemporary classical",
            "neo-classical", "neoclassical", "minimalism", "string quartet",
            "piano classical", "romantic classical", "sonata", "chorus",
            "showtunes", "musical", "musicals", "broadway", "theatre music",
            "chamber pop", "baroque pop",
            "classical music",
            // ES
            "clásica", "clasica", "música clásica", "musica clasica",
            "clásico", "clasico", "orquesta", "sinfonía", "sinfonia",
            "ópera", "barroco", "coro", "danzon", "danzón",
            // PT
            "clássico", "classico", "música clássica", "musica classica", "clássica",
            // FR
            "classique", "musique classique",
            // DE
            "klassik", "klassische musik",
            // IT
            "classica", "musica classica",
            // JA
            "クラシック", "クラシック音楽", "クラシカル", "演歌",
            // KO
            "클래식", "클래식 음악",
            // ZH
            "古典", "古典音乐", "古典音樂", "古典乐", "戏曲", "京剧", "昆曲" -> R.drawable.clasic_piano

            // ── ELECTRONIC / EDM ─────────────────────────────────────────────────
            "electronic", "edm", "techno", "house", "trance", "dubstep", "electro",
            "deep house", "progressive house", "tropical house", "future bass",
            "ambient house", "garage", "uk garage", "disco", "euro-disco",
            "idm", "psytrance", "goa", "goa trance", "big beat", "rave",
            "euro-techno", "euro-house", "club-house", "techno-industrial",
            "electronic music", "dance/electronic", "electronic/dance",
            // slash/conjunction variants
            "electronic/dance", "dance/electronic",
            // ES
            "electrónica", "electronica", "música electrónica", "musica electronica",
            // PT
            "eletrônica", "eletronica", "música eletrônica", "musica eletronica",
            "baile funk", "tecnobrega",
            // FR
            "électronique", "electronique", "musique électronique",
            "musique electronique", "électro",
            // DE
            "elektronisch", "elektronische musik", "elektro",
            // IT
            "elettronica", "musica elettronica",
            // JA
            "エレクトロニック", "電子音楽", "エレクトロ",
            // KO
            "일렉트로닉", "전자음악",
            // ZH
            "电子", "电子音乐", "電子", "電子音樂", "电音",
            // Fitness (upbeat)
            "workout", "gym", "fitness", "running", "cardio", "sports",
            "workout music",
            "ejercicio", "entrenamiento", "gimnasio", "deporte", "deportes",
            "exercício", "exercicio", "academia", "treino",
            "exercice", "sport", "workout musik", "musik für sport" -> R.drawable.electronic_sound

            // ── DRUM & BASS ───────────────────────────────────────────────────────
            "drum and bass", "d&b", "dnb", "jungle", "breakbeat", "breaks" -> R.drawable.drum

            // ── HARDSTYLE ─────────────────────────────────────────────────────────
            "hardstyle", "hardcore", "gabber" -> R.drawable.metal_guitar_2

            // ── CHILL / AMBIENT / NEW AGE ─────────────────────────────────────────
            "sleep", "relax", "meditation", "ambient", "chillout", "chill out",
            "chill", "downtempo", "new age", "spa", "nature sounds",
            "dark ambient", "drone", "psybient",
            "sleep music", "meditation music", "ambient music", "new age music",
            // ES
            "relajación", "relajacion", "meditación", "meditacion",
            "dormir", "música ambiental", "musica ambiental", "nueva era",
            "ambiente", "bienestar",
            // PT
            "relaxamento", "relaxação", "relaxacao", "meditação", "meditacao",
            // FR
            "méditation", "relaxation", "bien-être", "bien-etre",
            // DE
            "entspannung", "schlafmusik", "naturgeräusche",
            // IT
            "meditazione", "rilassamento", "musica rilassante",
            // JA
            "睡眠", "リラックス", "瞑想",
            // KO
            "명상", "힐링",
            // ZH
            "冥想", "放松", "睡眠", "新世纪", "新紀元", "禅", "佛教" -> R.drawable.rounded_alarm_24

            // ── COUNTRY / REGIONAL ────────────────────────────────────────────────
            "country", "bluegrass", "americana", "ranchera", "corrido", "corridos",
            "country music",
            // slash/conjunction
            "country/folk", "folk/country",
            // ES
            "regional mexicano", "regional mexicana", "música regional mexicana",
            "musica regional mexicana", "corridos del norte",
            // PT
            "sertanejo", "sertanejo universitário", "sertanejo universitario",
            "sertanejo raiz",
            // JA
            "カントリー",
            // KO
            "컨트리",
            // ZH
            "乡村", "乡村音乐" -> R.drawable.banjo

            // ── FOLK / ACOUSTIC / SINGER-SONGWRITER ───────────────────────────────
            "folk", "acoustic", "singer-songwriter", "folk & acoustic",
            "nueva canción", "nueva cancion", "fado",
            "indie folk", "folk pop", "dark folk", "gothic folk", "anti-folk",
            "folk music", "acoustic music",
            // slash/conjunction
            "folk/acoustic", "acoustic/folk", "singer/songwriter",
            // ES
            "folclore", "folklore", "música folclórica", "musica folklorica",
            "música folk", "musica folk", "trova", "nueva trova",
            "bambuco", "tonada",
            // PT
            "mpb folk",
            // FR
            "musique folk", "chanson folk",
            // DE
            "volksmusik", "volkslied", "volkslieder",
            // IT
            "folk italiano", "musica tradizionale", "folkloristica",
            // JA
            "フォーク", "フォークソング",
            // KO
            "포크",
            // ZH
            "民谣", "民謠", "民间音乐", "民間音樂", "民间歌曲" -> R.drawable.acoustic_guitar

            // ── R&B / SOUL / FUNK ─────────────────────────────────────────────────
            "r&b / soul", "rnb", "r&b", "soul", "funk", "motown",
            "neo-soul", "neo soul", "quiet storm", "slow jam", "ballad",
            "r&b music", "soul music", "funk music",
            // slash/conjunction
            "r&b/soul", "soul/r&b", "soul/funk", "funk/soul",
            // ES
            "rhythm and blues", "balada", "balada romántica", "balada romantica",
            "romántica", "romantica",
            // JA
            "ソウル", "ファンク",
            // KO
            "소울", "발라드",
            // ZH
            "灵魂乐", "放克", "靈魂樂", "節奏藍調", "节奏布鲁斯" -> R.drawable.synth_piano

            // ── LATIN — CONGA ─────────────────────────────────────────────────────
            "salsa", "samba", "mambo", "rumba", "cha-cha", "cha cha", "chacha",
            "son cubano", "son", "flamenco", "champeta", "cumbia villera",
            "guaracha", "timba", "landó", "lando", "festejo",
            "boogaloo", "son montuno", "salsa romántica", "salsa romantica",
            "salsa dura", "timba cubana", "mozambique", "rumba flamenca",
            "flamenco pop", "nuevo flamenco", "latin soul", "afrolatino",
            "duranguense", "cumbia sonidera", "mapalé", "mapale",
            "currulao", "garifuna",
            // Afro
            "afrobeat", "afrobeats", "afropop", "afro", "highlife",
            "soukous", "kizomba", "kuduro", "semba", "rebita", "kwaito",
            "amapiano", "gqom", "afro house", "afrohouse", "bongo flava",
            "juju", "makossa",
            "axé", "axe", "axé music" -> R.drawable.conga

            // ── LATIN — BONGOS ────────────────────────────────────────────────────
            "bachata", "tango", "bolero", "zouk",
            "tango nuevo", "new tango", "tango argentino",
            // ES
            "milonga", "zamba", "pasillo", "música criolla", "musica criolla",
            "milonga argentina", "pasillo colombiano", "zamba argentina" -> R.drawable.bongos

            // ── LATIN — DRUM ──────────────────────────────────────────────────────
            "merengue", "banda", "merengue urbano",
            // PT
            "pagode", "pagode baiano", "pagode baiana" -> R.drawable.drum

            // ── LATIN — MARACAS ───────────────────────────────────────────────────
            "cumbia", "mariachi", "marimba", "huapango", "porro",
            "bossa nova", "bossanova", "bossa", "soca", "calypso",
            // ES
            "chacarera", "cueca", "son jarocho", "joropo", "gaita",
            "música andina", "musica andina", "cumbia andina", "cumbia chilena",
            "punta", "música tropical", "musica tropical",
            "música llanera", "musica llanera", "quebradita",
            "huayno", "saya",
            // PT
            "frevo", "carimbó", "carimbo", "lambada", "piseiro", "pisadinha",
            "forró", "forro", "xote", "xaxado", "maracatu",
            // IT
            "tarantella",
            // World
            "samba-reggae",
            // JA
            "ボサノバ", "レゲエ",
            // KO
            "레게" -> R.drawable.maracas

            // ── LATIN — ACCORDION ─────────────────────────────────────────────────
            "norteño", "norteno", "tejano", "grupero",
            "polka", "klezmer", "musette",
            // ES
            "cuarteto", "vallenato",
            // PT
            "baião", "baiao", "música nordestina", "musica nordestina",
            // DE / World
            "cajun", "zydeco", "celtic", "irish",
            "folk scandinavia", "nordic" -> R.drawable.accordion

            // ── LATIN GENERAL ─────────────────────────────────────────────────────
            "latino", "latin", "latin pop", "urbano latino", "tropical",
            "latin alternative", "latin rock", "tropipop",
            // ES
            "música latina", "musica latina", "pop latinoamericano",
            "música latinoamericana", "musica latinoamericana" -> R.drawable.star_angle

            // ── REGGAE / SKA ──────────────────────────────────────────────────────
            "reggae", "ska", "dancehall", "roots reggae", "dub",
            "reggae music",
            // slash/conjunction
            "reggae/ska", "ska/reggae" -> R.drawable.maracas

            // ── WORLD / ETHNIC ────────────────────────────────────────────────────
            "world", "world music", "ethnic", "folk world & country",
            "traditional", "indigenous", "tribal", "global",
            "bollywood", "filmi", "bhangra", "carnatic", "hindustani",
            "ghazal", "qawwali", "rai", "chaabi", "arabic pop", "arab pop",
            "turkish pop",
            // ES
            "música del mundo", "musica del mundo", "música mundial",
            "musica mundial", "étnica", "etnica", "tradicional", "indígena", "indigena",
            "música tradicional", "musica tradicional",
            // PT
            "música do mundo",
            // FR
            "musique du monde", "musique africaine", "musique traditionnelle",
            // DE
            "weltmusik",
            // IT
            "musica del mondo", "musica tradizionale italiana",
            // JA
            "民謡", "日本民謡",
            // KO
            "국악", "민요", "한국 민요",
            // ZH
            "民族", "传统", "中国传统音乐", "國風", "国风", "中国风", "古风",
            "傳統", "民族音乐" -> R.drawable.rattle

            // ── GOSPEL / CHRISTIAN ────────────────────────────────────────────────
            "gospel", "christian", "christian rock", "ccm", "contemporary christian",
            "spiritual", "religious", "worship", "praise",
            // slash/conjunction
            "gospel/christian", "christian/gospel",
            // ES
            "música cristiana", "musica cristiana", "cristiana", "evangélica",
            "evangelica", "música gospel", "musica gospel", "música espiritual",
            "musica espiritual", "alabanza", "adoración", "adoracion",
            // PT
            "música cristã", "musica crista", "cristã", "crista", "louvores",
            // FR
            "évangile", "evangile", "musique chrétienne", "musique chretienne",
            "louanges", "cantiques",
            // DE
            "kirchenmusik",
            // IT
            "gospel italiano", "musica cristiana italiana",
            // ZH
            "福音", "基督教音乐", "圣歌", "赞美诗", "讚美詩" -> R.drawable.rounded_favorite_24

            // ── CHILDREN'S ────────────────────────────────────────────────────────
            "children's", "children", "kids", "nursery", "nursery rhymes",
            "baby", "lullaby", "lullabies",
            "kids music", "children music", "children's music",
            // ES
            "música infantil", "musica infantil", "infantil", "niños", "ninos",
            "canciones infantiles", "rondas", "nanas", "para niños", "para ninos",
            // PT
            "canções infantis", "cancoes infantis",
            // FR
            "musique pour enfants", "enfants", "comptines",
            // DE
            "kindermusik", "kinder", "kinderlieder", "kinderlied",
            // IT
            "musica per bambini", "bambini", "filastrocche", "canzoni per bambini",
            // JA
            "子供", "こども", "童謡", "子供の歌",
            // KO
            "어린이", "동요",
            // ZH
            "儿歌", "童謠", "童谣", "兒歌", "儿童" -> R.drawable.rattle

            // ── SPOKEN WORD / PODCAST / POETRY ───────────────────────────────────
            "spoken word", "poetry", "audiobook", "spoken",
            "podcast", "speech", "audio theatre", "audio theater",
            // slash/conjunction
            "comedy/spoken",
            // ES
            "palabra hablada", "poesía", "poesia", "audiolibro",
            // JA
            "朗読", "詩",
            // ZH
            "有声书", "朗诵" -> R.drawable.rounded_lyrics_24

            // ── COMEDY / HUMOR ────────────────────────────────────────────────────
            "comedy", "humor", "humour", "satire", "pranks",
            // ES
            "comedia", "humor musical",
            // FR
            "comédie", "comedie",
            // IT
            "commedia" -> R.drawable.rounded_celebration_24

            // ── CHRISTMAS / SEASONAL ─────────────────────────────────────────────
            "christmas", "holiday", "festive", "seasonal",
            "christmas music", "holiday music",
            // ES
            "navidad", "navideña", "navidena", "música navideña",
            "musica navidena", "villancicos", "aguinaldos", "posadas",
            // PT
            "natal", "músicas natalinas", "musicas natalinas", "música de natal",
            "musica de natal",
            // FR
            "noël", "noel", "musique de noël", "musique de noel",
            // DE
            "weihnachtsmusik", "weihnachten", "weihnachtslieder",
            // IT
            "natale", "musica natalizia", "canzoni di natale",
            // JA
            "クリスマス", "クリスマスソング",
            // KO
            "크리스마스",
            // ZH
            "圣诞", "聖誕", "圣诞节", "圣诞歌曲" -> R.drawable.rounded_celebration_24

            // ── OLDIES / RETRO ────────────────────────────────────────────────────
            "oldies", "retro", "80s", "90s", "70s", "60s", "50s",
            "classic hits", "throwback", "revival",
            // ES
            "viejitos", "clásicos", "clasicos", "viejos éxitos",
            "viejos exitos", "nostalgia",
            // PT
            "clássicos", "classicos",
            // FR
            "rétro", "années 80", "années 90",
            // DE
            "oldies deutsch",
            // JA
            "懐かしの曲", "昭和",
            // KO
            "옛날노래",
            // ZH
            "怀旧", "懷舊" -> R.drawable.rounded_schedule_24

            // ── SOUNDTRACK / OST / ANIME ─────────────────────────────────────────
            "soundtrack", "score", "film score", "movie tunes", "ost",
            "anime soundtrack", "anime", "trailer", "trailer music",
            "k-drama ost", "k-drama",
            // ES
            "banda sonora", "música de película", "musica de pelicula",
            "música de cine", "musica de cine", "música original", "musica original",
            // PT
            "trilha sonora", "trilha",
            // FR
            "bande originale", "bande-son", "bande son", "musique de film",
            // DE
            "filmmusik",
            // IT
            "colonna sonora",
            // JA
            "サウンドトラック", "映画音楽", "アニメ", "アニメソング", "アニソン",
            // KO
            "사운드트랙", "영화음악", "애니메이션",
            // ZH
            "原声", "电影原声", "影视原声", "动漫", "動漫" -> R.drawable.rounded_tv_24

            // ── GAMING ────────────────────────────────────────────────────────────
            "gaming", "video game music", "chiptune", "8-bit", "game music",
            // Generic "video game" strings a tagger might use
            "video game", "video games", "vgm", "game",
            "game soundtrack", "game ost", "video game ost",
            "video game soundtrack", "retro gaming", "arcade", "console music",
            "8-bit music", "chiptune music", "gaming music",
            // ES
            "música de videojuegos", "musica de videojuegos", "videojuegos",
            // PT
            "música de jogos", "musica de jogos",
            // JA
            "ゲーム音楽", "ゲームミュージック", "BGM",
            // KO
            "게임 음악", "게임음악",
            // ZH
            "游戏音乐", "遊戲音樂" -> R.drawable.rounded_touch_app_24

            // ── PARTY / DANCE ─────────────────────────────────────────────────────
            "party", "club", "dance", "dance music",
            // ES
            "fiesta", "baile", "música de baile", "musica de baile", "discoteca",
            // PT
            "festa", "dança", "danca",
            // FR
            "fête", "fete", "danse", "soirée", "soiree",
            // DE
            "tanz", "tanzmusik",
            // IT
            "discoteca", "ballo", "musica dance",
            // JA
            "ダンス", "ダンスミュージック",
            // KO
            "댄스", "댄스 음악",
            // ZH
            "舞曲", "舞蹈", "派对" -> R.drawable.rounded_celebration_24

            // ── FOCUS / STUDY ─────────────────────────────────────────────────────
            "focus", "study", "concentration", "study music", "focus music",
            // ES
            "concentración", "concentracion", "estudio", "música de estudio",
            "musica de estudio",
            // PT
            "concentração", "concentracao",
            // FR
            "concentration", "étude", "etude",
            // DE
            "konzentration", "lernen",
            // IT
            "concentrazione", "studio",
            // JA
            "集中", "勉強",
            // KO
            "공부", "집중",
            // ZH
            "专注", "學習", "学习", "專注" -> R.drawable.rounded_edit_24

            // ── EASY LISTENING / LOUNGE ───────────────────────────────────────────
            "easy listening", "lounge", "background music", "smooth",
            "easy", "lounge music",
            // ES
            "música suave", "musica suave", "música de fondo", "musica de fondo",
            // PT
            "música de ambiente",
            // FR
            "musique d'ambiance",
            // DE
            "hintergrundmusik",
            // IT
            "musica di sottofondo",
            // JA
            "イージーリスニング",
            // KO
            "이지 리스닝" -> R.drawable.rounded_headphones_24

            // ── INSTRUMENTAL / A CAPPELLA ─────────────────────────────────────────
            "instrumental", "acapella", "a cappella", "a capella",
            "instrumental music",
            // FR
            "musique instrumentale",
            // DE
            "instrumentalmusik",
            // IT
            "musica strumentale",
            // JA
            "インストゥルメンタル", "インスト",
            // ZH
            "器乐", "純音樂", "纯音乐", "轻音乐" -> R.drawable.rounded_music_note_24

            // ── RINGTONE / NOTIFICATION / SYSTEM SOUNDS ──────────────────────────
            "ringtone", "ringtones", "notification", "notification sound",
            "notification tone", "alert", "alert tone", "phone tone",
            "message tone", "alarm tone", "tone", "tones",
            // ES
            "tono", "tonos", "tono de llamada", "tonos de llamada",
            // PT
            "toque", "toque de celular", "toque de chamada" -> R.drawable.rounded_music_note_24

            // ── FAVORITES / HITS / CHARTS ─────────────────────────────────────────
            "favorites", "favourites", "favorite", "favourite",
            "greatest hits", "best of", "hits", "best hits", "top hits",
            "popular", "trending", "chart", "charts",
            // ES
            "favoritos", "favoritas", "éxitos", "exitos",
            "lo mejor de", "los mejores éxitos", "los mejores exitos",
            "más popular", "mas popular",
            // PT
            "sucessos", "melhores músicas", "melhores musicas",
            // FR
            "favoris", "meilleures chansons", "tubes",
            // DE
            "favoriten", "beste lieder",
            // IT
            "preferiti", "successi",
            // ZH
            "最爱", "精選", "精选", "热门" -> R.drawable.rounded_favorite_24

            // ── REMIX / DJ / MASHUP / PRODUCTION ─────────────────────────────────
            "remix", "remixes", "remix ep", "dj mix", "dj set", "dj",
            "continuous mix", "mashup", "mash-up", "mash up",
            "rework", "reworks", "edits", "bootleg remix",
            "extended mix", "radio edit", "club mix",
            "flip", "refix", "re-edit", "vip" -> R.drawable.electronic_sound

            // ── LIVE / CONCERT ────────────────────────────────────────────────────
            "live", "live music", "concert", "live concert", "live session",
            "live at", "mtv unplugged", "live recording", "live performance",
            "live album",
            // ES
            "en vivo", "concierto", "en directo", "directo",
            // PT
            "ao vivo", "concerto",
            // FR
            "en direct", "concert live",
            // DE
            "live konzert", "konzert",
            // IT
            "dal vivo", "concerto live",
            // JA
            "ライブ", "コンサート",
            // KO
            "라이브" -> R.drawable.rounded_music_note_24

            // ── UNPLUGGED / COVER / TRIBUTE / DEMO ───────────────────────────────
            "unplugged", "cover", "covers", "cover song", "cover songs",
            "tribute", "acoustic cover", "acoustic session",
            "b-sides", "b sides", "rarities", "demo", "demos",
            "bootleg", "outtake", "outtakes",
            // ES
            "versión acústica", "version acustica", "versiones",
            // PT
            "acústico", "acustico", "versões" -> R.drawable.acoustic_guitar

            // ── MOOD — HAPPY / UPBEAT / ENERGETIC ────────────────────────────────
            "happy", "upbeat", "energetic", "feel good", "feelgood",
            "fun", "euphoric", "cheerful", "joyful", "positive", "hype",
            // ES
            "alegre", "animado", "animada", "feliz", "divertido", "divertida",
            // PT
            "animado", "animada",
            // FR
            "joyeux", "joyeuse", "gai",
            // DE
            "fröhlich",
            // IT
            "allegro", "gioioso" -> R.drawable.rounded_celebration_24

            // ── MOOD — ROMANTIC / LOVE ────────────────────────────────────────────
            "romantic", "romance", "love", "love songs", "love music",
            "sensual", "passionate", "intimate",
            // ES
            "amor", "canciones de amor", "romantico", "romántico",
            // PT
            "amor", "romântico", "romântica",
            // FR
            "romantique", "amour",
            // DE
            "romantisch", "liebeslieder",
            // IT
            "romantico", "romantica", "amore",
            // ZH
            "情歌", "爱情", "愛情" -> R.drawable.rounded_favorite_24

            // ── MOOD — SAD / EMOTIONAL / MELANCHOLIC ──────────────────────────────
            "sad", "melancholic", "melancholy", "emotional", "heartbreak",
            "breakup", "lonely", "tearjerker", "bittersweet", "somber",
            // ES
            "triste", "tristeza", "melancolico", "melancólico", "desamor",
            // PT
            "triste", "tristeza", "melancólico",
            // FR
            "triste", "mélancolique",
            // DE
            "traurig", "melancholisch",
            // IT
            "malinconico",
            // ZH
            "悲伤", "忧郁" -> R.drawable.synth_piano

            // ── MOOD — PEACEFUL / CALM / SOOTHING ────────────────────────────────
            "peaceful", "calm", "soothing", "tranquil", "serene",
            "gentle", "quiet", "mellow", "soft",
            // ES
            "tranquilo", "tranquila", "calmado", "calmada",
            // PT
            "tranquilo", "calmo",
            // FR
            "calme", "paisible", "doux",
            // DE
            "ruhig", "sanft",
            // IT
            "tranquillo", "calmo",
            // ZH
            "轻柔", "安静" -> R.drawable.rounded_headphones_24

            // ── ACTIVITY — DRIVING / TRAVEL / ROAD ───────────────────────────────
            "driving", "road trip", "travel", "commute", "car music",
            "highway", "cruising",
            // ES
            "manejar", "conducir", "viaje", "carretera", "música para manejar",
            // PT
            "dirigindo", "viagem",
            // FR
            "conduite", "voyage",
            // DE
            "autofahren", "reise" -> R.drawable.electronic_sound

            // ── ACTIVITY — MORNING / WAKE UP ──────────────────────────────────────
            "morning", "wake up", "wakeup", "good morning", "sunrise",
            "breakfast", "morning routine",
            // ES
            "mañana", "despertar", "buenos días", "buenos dias",
            // PT
            "manhã", "manha", "acordar",
            // FR
            "matin", "réveil",
            // DE
            "morgen", "aufwachen",
            // IT
            "mattina", "risveglio",
            // JA
            "朝", "目覚め" -> R.drawable.rounded_alarm_24

            // ── ACTIVITY — NIGHT / EVENING ────────────────────────────────────────
            "night", "late night", "nighttime", "midnight", "evening",
            "after hours", "nocturnal",
            // ES
            "noche", "tarde", "medianoche",
            // PT
            "noite",
            // FR
            "nuit",
            // DE
            "nacht",
            // IT
            "notte",
            // ZH
            "夜晚", "夜曲" -> R.drawable.rounded_alarm_24

            // ── ACTIVITY — WORK / OFFICE ──────────────────────────────────────────
            "work", "office", "work music", "office music",
            // ES
            "trabajo", "oficina",
            // PT
            "trabalho",
            // FR
            "travail", "bureau",
            // DE
            "arbeit", "büro",
            // IT
            "lavoro", "ufficio" -> R.drawable.rounded_edit_24

            // ── SPECIAL — WEDDING / GRADUATION / FORMAL ───────────────────────────
            "wedding", "wedding music", "marriage", "matrimony", "graduation",
            "ceremony", "formal",
            // ES
            "boda", "matrimonio", "graduación", "graduacion",
            "quinceañera", "quinceanera", "quince años", "quince anos",
            // PT
            "casamento", "formatura",
            // FR
            "mariage",
            // DE
            "hochzeit", "hochzeitsmusik",
            // IT
            "matrimonio", "nozze",
            // ZH
            "婚礼", "毕业" -> R.drawable.clasic_piano

            // ── SPECIAL — BIRTHDAY ────────────────────────────────────────────────
            "birthday", "birthday music", "happy birthday",
            // ES
            "cumpleaños", "feliz cumpleaños", "feliz cumpleanos",
            // PT
            "aniversário", "aniversario", "parabéns", "parabens",
            // FR
            "anniversaire", "joyeux anniversaire",
            // DE
            "geburtstag", "zum geburtstag",
            // IT
            "compleanno", "buon compleanno",
            // ZH
            "生日", "生日歌" -> R.drawable.rounded_celebration_24

            // ── DECADES EXTENSION ─────────────────────────────────────────────────
            "2000s", "00s", "2010s", "10s", "2020s", "20s",
            "millennium", "vintage", "classic",
            // ES
            "años 2000", "los 2000", "años 2010",
            // ZH
            "复古" -> R.drawable.rounded_schedule_24

            // ── AUDIO QUALITY / AUDIOPHILE ────────────────────────────────────────
            "hifi", "hi-fi", "hi fi", "lossless", "flac", "audiophile",
            "high fidelity", "high quality", "hd audio", "hi-res",
            "binaural", "asmr", "dolby", "surround",
            // ES
            "alta fidelidad", "alta calidad",
            // PT
            "alta fidelidade" -> R.drawable.rounded_headphones_24

            // ── SOUND EFFECTS / NATURE / NOISE ────────────────────────────────────
            "sound effects", "sfx", "fx", "nature", "rain", "ocean", "waves",
            "birds", "birdsong", "white noise", "pink noise", "brown noise",
            "thunder", "storm", "wind", "waterfall", "forest", "fire",
            "frequency", "binaural beats", "432hz", "528hz", "174hz",
            // ES
            "efectos de sonido", "sonidos de la naturaleza", "lluvia",
            // PT
            "efeitos sonoros", "sons da natureza", "chuva",
            // DE
            "regengeräusche",
            // JA
            "自然音", "雨音",
            // ZH
            "自然声音", "雨声" -> R.drawable.rounded_alarm_24

            // ── GENERIC / VARIOUS / COMPILATION ──────────────────────────────────
            // Catch-all for untagged or poorly-tagged libraries
            "music", "audio", "sound", "track", "song", "songs",
            "various", "various artists", "va", "compilation", "compil",
            "mix", "mixtape", "playlist", "collection", "medley",
            "new", "new music", "latest", "recent", "other", "misc",
            "miscellaneous", "general", "uncategorized",
            // ES
            "música", "musica", "canción", "cancion", "canciones",
            "varios", "variado", "varios artistas", "recopilatorio",
            "colección", "coleccion",
            // PT
            "canção", "cancao", "canções", "cancoes",
            "vários", "coletânea", "coletanea",
            // FR
            "musique", "compilation fr",
            // DE
            "musik", "sammlung",
            // IT
            "musica generica", "raccolta",
            // JA
            "音楽", "曲", "楽曲",
            // KO
            "음악", "노래",
            // ZH
            "音乐", "歌曲", "音樂" -> R.drawable.rounded_library_music_24

            "unknown" -> R.drawable.rounded_question_mark_24
            else -> R.drawable.rounded_library_music_24
        }
    }
}
