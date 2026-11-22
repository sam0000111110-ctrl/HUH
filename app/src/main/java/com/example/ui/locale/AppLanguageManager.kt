package com.example.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Supported Application Interface Languages
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String,
    val isRtl: Boolean
) {
    ARABIC(
        code = "ar",
        displayName = "Arabic",
        nativeName = "العربية",
        flagEmoji = "🇸🇦",
        isRtl = true
    ),
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        flagEmoji = "🇺🇸",
        isRtl = false
    ),
    FRENCH(
        code = "fr",
        displayName = "French",
        nativeName = "Français",
        flagEmoji = "🇫🇷",
        isRtl = false
    ),
    SPANISH(
        code = "es",
        displayName = "Spanish",
        nativeName = "Español",
        flagEmoji = "🇪🇸",
        isRtl = false
    ),
    TURKISH(
        code = "tr",
        displayName = "Turkish",
        nativeName = "Türkçe",
        flagEmoji = "🇹🇷",
        isRtl = false
    );

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ARABIC
        }
    }
}

/**
 * UI String dictionary mapped by key and language
 */
object AppStrings {
    private val translations = mapOf(
        // Tabs
        "tab_match" to mapOf(
            AppLanguage.ARABIC to "المطابقة",
            AppLanguage.ENGLISH to "Match",
            AppLanguage.FRENCH to "Rencontre",
            AppLanguage.SPANISH to "Coincidencia",
            AppLanguage.TURKISH to "Eşleşme"
        ),
        "tab_live" to mapOf(
            AppLanguage.ARABIC to "بث مباشر",
            AppLanguage.ENGLISH to "Live Streams",
            AppLanguage.FRENCH to "En direct",
            AppLanguage.SPANISH to "En vivo",
            AppLanguage.TURKISH to "Canlı Yayın"
        ),
        "tab_store" to mapOf(
            AppLanguage.ARABIC to "المتجر",
            AppLanguage.ENGLISH to "Store",
            AppLanguage.FRENCH to "Boutique",
            AppLanguage.SPANISH to "Tienda",
            AppLanguage.TURKISH to "Mağaza"
        ),
        "tab_agency" to mapOf(
            AppLanguage.ARABIC to "الوكالات",
            AppLanguage.ENGLISH to "Agencies",
            AppLanguage.FRENCH to "Agences",
            AppLanguage.SPANISH to "Agencias",
            AppLanguage.TURKISH to "Ajanslar"
        ),
        "tab_profile" to mapOf(
            AppLanguage.ARABIC to "حسابي",
            AppLanguage.ENGLISH to "Profile",
            AppLanguage.FRENCH to "Profil",
            AppLanguage.SPANISH to "Perfil",
            AppLanguage.TURKISH to "Profilim"
        ),

        // Settings / Preferences Headers
        "settings_title" to mapOf(
            AppLanguage.ARABIC to "الإعدادات والتفضيلات",
            AppLanguage.ENGLISH to "Settings & Preferences",
            AppLanguage.FRENCH to "Paramètres et Préférences",
            AppLanguage.SPANISH to "Ajustes y Preferencias",
            AppLanguage.TURKISH to "Ayarlar ve Tercihler"
        ),
        "language_menu_title" to mapOf(
            AppLanguage.ARABIC to "لغة التطبيق (Interface Language)",
            AppLanguage.ENGLISH to "App Language (Interface Language)",
            AppLanguage.FRENCH to "Langue de l'application",
            AppLanguage.SPANISH to "Idioma de la aplicación",
            AppLanguage.TURKISH to "Uygulama Dili"
        ),
        "language_menu_subtitle" to mapOf(
            AppLanguage.ARABIC to "تغيير لغة العرض واتجاه واجهة المستخدم",
            AppLanguage.ENGLISH to "Change display language & layout direction",
            AppLanguage.FRENCH to "Changer la langue d'affichage et l'orientation",
            AppLanguage.SPANISH to "Cambiar el idioma de visualización y la orientación",
            AppLanguage.TURKISH to "Görüntüleme dilini ve yönünü değiştir"
        ),
        "select_language_dialog_title" to mapOf(
            AppLanguage.ARABIC to "اختر لغة التطبيق",
            AppLanguage.ENGLISH to "Select App Language",
            AppLanguage.FRENCH to "Sélectionnez la langue",
            AppLanguage.SPANISH to "Selecciona el idioma",
            AppLanguage.TURKISH to "Uygulama Dilini Seçin"
        ),
        "dark_mode_title" to mapOf(
            AppLanguage.ARABIC to "الوضع الداكن (Dark Mode)",
            AppLanguage.ENGLISH to "Dark Mode",
            AppLanguage.FRENCH to "Mode Sombre",
            AppLanguage.SPANISH to "Modo Oscuro",
            AppLanguage.TURKISH to "Karanlık Mod"
        ),
        "light_mode_title" to mapOf(
            AppLanguage.ARABIC to "الوضع الفاتح (Light Mode)",
            AppLanguage.ENGLISH to "Light Mode",
            AppLanguage.FRENCH to "Mode Clair",
            AppLanguage.SPANISH to "Modo Claro",
            AppLanguage.TURKISH to "Aydınlık Mod"
        ),
        "dark_mode_desc" to mapOf(
            AppLanguage.ARABIC to "واجهة ليلية مريحة للعين",
            AppLanguage.ENGLISH to "Comfortable eye-safe night interface",
            AppLanguage.FRENCH to "Interface de nuit confortable pour les yeux",
            AppLanguage.SPANISH to "Interfaz nocturna cómoda para la vista",
            AppLanguage.TURKISH to "Göz yormayan gece arayüzü"
        ),
        "light_mode_desc" to mapOf(
            AppLanguage.ARABIC to "واجهة نهارية عالية الوضوح والتباين",
            AppLanguage.ENGLISH to "High contrast, crisp daytime layout",
            AppLanguage.FRENCH to "Disposition de jour nette et contrastée",
            AppLanguage.SPANISH to "Diseño diurno de alto contraste y nítido",
            AppLanguage.TURKISH to "Yüksek kontrastlı gündüz arayüzü"
        ),
        "auto_translate_title" to mapOf(
            AppLanguage.ARABIC to "الترجمة الفورية التلقائية",
            AppLanguage.ENGLISH to "Real-time Chat Translation",
            AppLanguage.FRENCH to "Traduction instantanée du chat",
            AppLanguage.SPANISH to "Traducción de chat en tiempo real",
            AppLanguage.TURKISH to "Gerçek Zamanlı Sohbet Çevirisi"
        ),
        "auto_translate_desc" to mapOf(
            AppLanguage.ARABIC to "ترجمة المحادثات الأجنبية فوراً",
            AppLanguage.ENGLISH to "Instantly translate incoming messages",
            AppLanguage.FRENCH to "Traduire instantanément les messages entrants",
            AppLanguage.SPANISH to "Traducir instantáneamente mensajes entrantes",
            AppLanguage.TURKISH to "Gelen mesajları anında çevir"
        ),
        "simulcast_title" to mapOf(
            AppLanguage.ARABIC to "بث الفيديو التكيفي (Simulcast)",
            AppLanguage.ENGLISH to "Adaptive Video Streaming (Simulcast)",
            AppLanguage.FRENCH to "Streaming Vidéo Adaptatif (Simulcast)",
            AppLanguage.SPANISH to "Transmisión de video adaptativa",
            AppLanguage.TURKISH to "Uyarlanabilir Video Akışı"
        ),
        "simulcast_desc" to mapOf(
            AppLanguage.ARABIC to "توفير الباندويدث حسب سرعة الإنترنت",
            AppLanguage.ENGLISH to "Conserves bandwidth based on connection",
            AppLanguage.FRENCH to "Économise la bande passante selon la connexion",
            AppLanguage.SPANISH to "Ahorra ancho de banda según tu conexión",
            AppLanguage.TURKISH to "Bağlantı hızına göre veri tasarrufu sağlar"
        ),
        "notifications_center_title" to mapOf(
            AppLanguage.ARABIC to "مركز إشعارات العمولات والإحالات",
            AppLanguage.ENGLISH to "Commissions & Referrals Center",
            AppLanguage.FRENCH to "Centre des commissions et parrainages",
            AppLanguage.SPANISH to "Centro de comisiones y referencias",
            AppLanguage.TURKISH to "Komisyonlar ve Bildirimler Merkezi"
        ),
        "privacy_section_title" to mapOf(
            AppLanguage.ARABIC to "الخصوصية المتقدمة (البروفايل الثانوي المخفي)",
            AppLanguage.ENGLISH to "Advanced Privacy (Hidden Secondary Profile)",
            AppLanguage.FRENCH to "Confidentialité avancée (Profil secondaire masqué)",
            AppLanguage.SPANISH to "Privacidad avanzada (Perfil secundario oculto)",
            AppLanguage.TURKISH to "Gelişmiş Gizlilik (Gizli İkincil Profil)"
        ),
        "logout_button" to mapOf(
            AppLanguage.ARABIC to "تسجيل الخروج",
            AppLanguage.ENGLISH to "Sign Out",
            AppLanguage.FRENCH to "Déconnexion",
            AppLanguage.SPANISH to "Cerrar Sesión",
            AppLanguage.TURKISH to "Çıkış Yap"
        ),
        "cancel" to mapOf(
            AppLanguage.ARABIC to "إلغاء",
            AppLanguage.ENGLISH to "Cancel",
            AppLanguage.FRENCH to "Annuler",
            AppLanguage.SPANISH to "Cancelar",
            AppLanguage.TURKISH to "İptal"
        ),
        "confirm" to mapOf(
            AppLanguage.ARABIC to "تأكيد",
            AppLanguage.ENGLISH to "Confirm",
            AppLanguage.FRENCH to "Confirmer",
            AppLanguage.SPANISH to "Confirmar",
            AppLanguage.TURKISH to "Onayla"
        )
    )

    fun get(key: String, language: AppLanguage): String {
        return translations[key]?.get(language) ?: translations[key]?.get(AppLanguage.ARABIC) ?: key
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ARABIC }

@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}
