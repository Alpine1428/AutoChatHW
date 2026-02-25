package com.holyworld.autoreply.ai;

import com.holyworld.autoreply.HolyWorldAutoReply;

import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Response engine that matches player messages to appropriate moderator responses.
 * Built from extensive log analysis of real moderator interactions during checks.
 */
public class ResponseEngine {

    // Track conversation state per player
    private final ConcurrentHashMap<String, PlayerState> playerStates = new ConcurrentHashMap<>();

    // Response categories
    private final List<ResponseRule> rules = new ArrayList<>();

    public ResponseEngine() {
        initializeRules();
    }

    // ======================== PLAYER STATE ========================
    
    private static class PlayerState {
        long checkStartTime;
        int messageCount = 0;
        boolean askedForAnydesk = false;
        boolean gaveCodes = false;
        boolean warnedTime = false;
        boolean offeredConfession = false;
        boolean mentionedRudesk = false;
        boolean mentionedRustdesk = false;
        String lastResponseCategory = "";
        long lastMessageTime = 0;
        List<String> recentMessages = new ArrayList<>();
        
        PlayerState() {
            this.checkStartTime = System.currentTimeMillis();
        }
        
        long getElapsedMinutes() {
            return (System.currentTimeMillis() - checkStartTime) / 60000;
        }
        
        int getRemainingMinutes() {
            int elapsed = (int) getElapsedMinutes();
            return Math.max(1, 7 - elapsed);
        }
    }

    // ======================== RESPONSE RULE ========================
    
    @FunctionalInterface
    private interface ResponseMatcher {
        boolean matches(String message, String lowerMessage, PlayerState state, String playerName);
    }
    
    @FunctionalInterface
    private interface ResponseGenerator {
        String generate(String message, String lowerMessage, PlayerState state, String playerName);
    }
    
    private static class ResponseRule {
        final String category;
        final int priority; // higher = checked first
        final ResponseMatcher matcher;
        final ResponseGenerator generator;
        
        ResponseRule(String category, int priority, ResponseMatcher matcher, ResponseGenerator generator) {
            this.category = category;
            this.priority = priority;
            this.matcher = matcher;
            this.generator = generator;
        }
    }

    // ======================== UTILITY ========================
    
    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
    
    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
    
    private static boolean matchesPattern(String text, String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    
    private static boolean isNumericCode(String text) {
        String cleaned = text.replaceAll("[\\s\\-]", "");
        return cleaned.length() >= 6 && cleaned.matches("\\d+");
    }
    
    private static boolean looksLikeAnydeskCode(String text) {
        String cleaned = text.replaceAll("[\\s\\-]", "");
        return cleaned.matches("\\d{9,10}");
    }
    
    private static boolean looksLikeRudeskCode(String text) {
        String cleaned = text.replaceAll("[\\s\\-]", "");
        return cleaned.matches("\\d{6,10}");
    }

    // ======================== RULE INITIALIZATION ========================
    
    private void initializeRules() {
        
        // ============================================================
        // PRIORITY 100: INSULTS AND TOXICITY -> BAN
        // ============================================================
        
        rules.add(new ResponseRule("insult_ban", 100,
            (msg, lower, state, name) -> containsAny(lower,
                "нахуй", "нахуи", "пошел нах", "пошёл нах", "нах пошел", "нах пошёл",
                "иди нах", "пиздец", "пизд", "хуй", "хуи", "хуе", "хуё",
                "ебал", "ебан", "ебат", "ебу", "еба", "ёба",
                "сука", "суки", "сучка", "сучар",
                "бляд", "блядь", "блять", "бля ",
                "далбаеб", "долбаеб", "долбоеб", "далбоеб", "дебил",
                "мразь", "мрази", "урод",
                "гандон", "гондон", "конд",
                "пидор", "пидр", "педик",
                "чмо", "чмошник",
                "безмамн", "мертвой мам", "мёртвой мам",
                "твою мат", "твою мать", "маму ебал", "маме пизд",
                "пузо вырезал", "организм",
                "сын бляд", "сын свинь",
                "сосо", "соси", "саси",
                "хуесос", "хуёсос"),
            (msg, lower, state, name) -> null // Return null to signal "ban" action
        ));
        
        // ============================================================
        // PRIORITY 95: EXPLICIT CONFESSION ("я софт", "я читер", "я чит", "признание", etc.)
        // ============================================================
        
        rules.add(new ResponseRule("confession_explicit", 95,
            (msg, lower, state, name) -> containsAny(lower,
                "я софт", "я читер", "я чит ", "я с софт", "я с читами", "я с читам",
                "я играю с чит", "у меня софт", "у меня чит",
                "у меня читы", "у меня x-ray", "у меня xray", "у меня х-рей",
                "бань нахуй", "бань нах", "банте нах",
                "бб короче", "я читор", "я чит бань",
                "я с модом", "я с софтом",
                "с софтом бань", "хочеш бань", "хочешь бань",
                "у меня селестиал", "у меня celestial",
                "у меня night", "у меня найт",
                "я чит баньте", "я читер бб",
                "признание ентити", "признание entity",
                "за хранения забань", "у меня всего x-ray",
                "все равно айпи сменю",
                "все равно на этот акк",
                "всё равно на этот акк",
                "мне все равно на этот",
                "мне всё равно на этот"),
            (msg, lower, state, name) -> null // Signal ban for confession
        ));
        
        // ============================================================
        // PRIORITY 94: LEAVE THREATS / ACTUAL LEAVING
        // ============================================================
        
        rules.add(new ResponseRule("leave_threat", 94,
            (msg, lower, state, name) -> containsAny(lower,
                "бб", "bb ", " bb", "бб ", " бб",
                "bb all", "бб всем", "all bb",
                "удачи", "пока",
                "лад баньте", "ладно баньте", "ладна банте",
                "давай бан", "баньте", "бань ",
                "я жду бан", "мне бан",
                "качать не охота", "качать не буду",
                "не буду ничего скачивать",
                "не хочу раст", "лив ток"),
            (msg, lower, state, name) -> {
                // Some of these are confessions, some are rage quits
                if (containsAny(lower, "удачи")) {
                    return pick("Спасибо за сотрудничество", "Удачи");
                }
                if (containsAny(lower, "бб", "bb", "пока")) {
                    return null; // ban signal
                }
                if (containsAny(lower, "баньте", "бань ", "давай бан", "я жду бан")) {
                    return null; // ban signal
                }
                if (containsAny(lower, "качать не охота", "качать не буду", "не буду ничего", "не хочу раст")) {
                    return null; // ban for refusal
                }
                return null;
            }
        ));
        
        // ============================================================
        // PRIORITY 93: EXPLICIT REFUSAL
        // ============================================================
        
        rules.add(new ResponseRule("explicit_refusal", 93,
            (msg, lower, state, name) -> containsAny(lower,
                "отпусти", "отпустите",
                "не хочу давай", "не буду",
                "мне лень", "забань на минимальн",
                "эту залупу качать", "не хочу бана",
                "я не буду ничего",
                "мне ни разрешают", "мне не разрешают",
                "не могу понять", "я не умею"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "мне лень", "не хочу", "не буду", "залупу")) {
                    return null; // ban
                }
                if (containsAny(lower, "отпусти")) {
                    return pick(
                        "Скачивай анидеск",
                        "Аник жду",
                        "Скачивай аник"
                    );
                }
                if (containsAny(lower, "не умею", "не могу понять")) {
                    return pick(
                        "Инструкция в чате",
                        "Все инструкции в лс",
                        "Вся инструкция в чате"
                    );
                }
                if (containsAny(lower, "не разрешают", "ни разрешают")) {
                    return pick(
                        "Инструкция есть в чате",
                        "Отказ/признание?"
                    );
                }
                return "Скачивай анидеск";
            }
        ));
        
        // ============================================================
        // PRIORITY 90: CONFESSION KEYWORDS (shorter)
        // ============================================================
        
        rules.add(new ResponseRule("confession_short", 90,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.equals("признание") || trimmed.equals("признаюсь") ||
                       trimmed.equals("признаю") || trimmed.equals("го признание") ||
                       trimmed.equals("ну газ признание лад") ||
                       containsAny(lower, "признаюсь что", "я признаюсь", "я признаюс",
                           "хорошо я признаюсь", "ладно я софт");
            },
            (msg, lower, state, name) -> null // ban for confession
        ));
        
        // ============================================================  
        // PRIORITY 85: ANYDESK/RUDESK/RUSTDESK CODE SUBMISSION
        // ============================================================
        
        rules.add(new ResponseRule("code_submit", 85,
            (msg, lower, state, name) -> {
                String cleaned = msg.replaceAll("[\\s\\-]", "");
                return cleaned.matches("\\d{6,10}");
            },
            (msg, lower, state, name) -> {
                state.gaveCodes = true;
                return pick(
                    "Принимай",
                    "+",
                    "Ща подключусь",
                    "Принимай запрос",
                    "Грузит"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 82: DISCORD OFFER
        // ============================================================
        
        rules.add(new ResponseRule("discord_offer", 82,
            (msg, lower, state, name) -> containsAny(lower,
                "через дс", "давай дс", "дс можно", "го дс", "го в дс",
                "го через дс", "можно дс", "мб дс", "по дс",
                "давай в дс", "пошли дс", "давайте дс",
                "го по дс", "го по диск", "давай по дс",
                "через дискорд", "можно дискорд", "го дискорд",
                "давай через дс", "давай в дискорд",
                "мой дс", "мой дискорд",
                "могу в дс", "могу дс",
                "через д ", "в дс", "в дискорд",
                "go cheres ds", "go w ds",
                "'nj lc", "это дс", "я дс кинул",
                "дс пойти", "в звонок", "пойдем в звонок"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "мой дс")) {
                    return pick(
                        "По дс проверки не проводим",
                        "Анидеск скачивай",
                        "-"
                    );
                }
                return pick(
                    "-",
                    "Нет",
                    "По дс проверки не проводим",
                    "Анидеск скачивай",
                    "Скачивай аник"
                );
            }
        ));

        // ============================================================
        // PRIORITY 81: VK / TG / OTHER PLATFORM OFFERS
        // ============================================================
        
        rules.add(new ResponseRule("other_platform", 81,
            (msg, lower, state, name) -> containsAny(lower,
                "через вк", "давай вк", "го вк", "го в вк",
                "можно вк", "по вк",
                "через тг", "давай тг", "го тг", "тг можно",
                "можно тг", "есть тг", "есть вк",
                "можно в лс", "могу в лс", "кому в лс",
                "в коменты", "могу в тг", "могу в вк",
                "в лс напишу", "демонстрация", "демку"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "кому в лс", "могу в лс", "можно в лс")) {
                    return pick("Мне", "Принимай");
                }
                return pick("-", "Скачивай аник");
            }
        ));
        
        // ============================================================
        // PRIORITY 80: GREETING
        // ============================================================
        
        rules.add(new ResponseRule("greeting", 80,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return state.messageCount <= 2 && containsAny(trimmed,
                    "привет", "прив", "хай", "хелло", "hello", "hi ",
                    "ку ", "qq", "здравств", "прывект", "приветик",
                    "er", "ку");
            },
            (msg, lower, state, name) -> {
                if (containsAny(lower, "привет я не читер")) {
                    return pick(
                        "Привет давай аник",
                        "Привет скачивай анидеск",
                        "Привет жду аник"
                    );
                }
                return pick(
                    "Это проверка на читы, у Вас есть 7 мин. чтобы скинуть AnyDesk и пройти проверку! Признание уменьшает наказание! В случае отказа/выхода/игнора - Бан без перепроверки! В браузере пиши anydesk com - скачивай, запускай и скидывай цифры",
                    "Привет! Жду аник",
                    "qq жду аник",
                    "Приветики) Жду аник"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 78: ASKING "WHY CHECK" / "FOR WHAT" / "REASON"
        // ============================================================
        
        rules.add(new ResponseRule("reason_ask", 78,
            (msg, lower, state, name) -> containsAny(lower,
                "за что", "причина", "зачем", "за что прове",
                "зачто", "почему вызвал", "за что вызвал",
                "почему меня", "что я сделал", "что я зделал",
                "а чо решил вызвать", "что случилось",
                "а за что", "за что собственно",
                "я ничего не делал", "я стоял",
                "поч вызвал", "для чего",
                "а за что проверка", "за что проверка",
                "какая проверка", "а чё это",
                "чего блять", "стой а можно",
                "я тока зашёл", "я только зашел",
                "я возле дк стоял", "я бутылочки набирал",
                "я зельки варил", "я сижу шахту копаю",
                "я просто играл", "я просто игиал",
                "я на спавне был", "что за прова",
                "а щас то за что", "что я зделал",
                "чего", "в чем причина", "зачем вызвал"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "причина", "в чем причина")) {
                    return pick(
                        "Многочисленные репорты",
                        "Репорты",
                        "Модератор в праве не разглашать причину проверки игроку",
                        "За все хорошее"
                    );
                }
                if (containsAny(lower, "повод", "из за реп", "из-за реп", "а чеки")) {
                    return pick("Да", "Репорты", "+");
                }
                return pick(
                    "За все хорошее",
                    "Репорты",
                    "Надо",
                    "Многочисленные репорты",
                    "Играл"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 76: "I'M CLEAN" / "NO CHEATS"
        // ============================================================
        
        rules.add(new ResponseRule("claim_clean", 76,
            (msg, lower, state, name) -> containsAny(lower,
                "я не читер", "я не читар", "я не софт",
                "я чист", "у меня нет читов", "у меня нету читов",
                "без читов", "без софта", "я ансофт", "ансофт",
                "я 100% ансофт", "я не использую",
                "я не читала", "я не софтер",
                "я легит", "я без",
                "я готов пройти", "готов пройти проверку",
                "я не виноват"),
            (msg, lower, state, name) -> pick(
                "Скачивай аник",
                "Верю скачивай",
                "Аник жду",
                "Скачивай анидеск",
                "Ну я жду"
            )
        ));
        
        // ============================================================
        // PRIORITY 75: ASKING ABOUT ANYDESK / WHAT IS IT
        // ============================================================
        
        rules.add(new ResponseRule("what_is_anydesk", 75,
            (msg, lower, state, name) -> containsAny(lower,
                "что за аник", "что такое аник", "что за анидеск",
                "что такое анидеск", "а що таке", "как им пользоват",
                "что это за прог", "что за прога", "это что за",
                "не знаю что это", "а чё это",
                "удаленный доступ", "это типо ты в моем",
                "типо ты в моем пк", "будешь лазать",
                "зачем", "для чего это",
                "а как анідеск", "а як анідеск",
                "что за ани деск", "что такое ани деск",
                "че за прога это", "анидеск это что",
                "это что", "а что это"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "типо ты в моем", "будешь лазать", "управлять моим")) {
                    return "+";
                }
                return pick(
                    "Программа удаленного доступа",
                    "Удаленный доступ",
                    "Программа такая",
                    "Скачиваешь и включаешь",
                    "Проверка"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 74: DOWNLOADING ANYDESK - STATUS
        // ============================================================
        
        rules.add(new ResponseRule("downloading", 74,
            (msg, lower, state, name) -> containsAny(lower,
                "скачиваю", "скачиваеться", "скачивается", "скачиваетсяя",
                "качаю", "качается", "загружается", "грузит",
                "устанавливаю", "устанавливается", "устанвливается",
                "устанавливаюю", "cкачиваю", "я качаю",
                "я скачиваю", "скачиваетьс",
                "пачти скачался", "почти скачал",
                "немного осталось", "ок ща скачаю",
                "щас скачаю", "ща скачаю",
                "сек аник скачаю", "сек скачаю",
                "загрузил", "скачал",
                "жди качаю", "жду качаю",
                "ок скачаю"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "скачал", "загрузил", "скачался")) {
                    return pick(
                        "Кидай код",
                        "Кидай длинный код",
                        "Открывай его",
                        "Давай код"
                    );
                }
                int remaining = state.getRemainingMinutes();
                return pick(
                    "Жду",
                    remaining + " min",
                    remaining + " минут",
                    "Жду жду",
                    "Время идет"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 73: CAN'T DOWNLOAD / DOESN'T WORK
        // ============================================================
        
        rules.add(new ResponseRule("cant_download", 73,
            (msg, lower, state, name) -> containsAny(lower,
                "не скачивается", "не качается", "не загружается",
                "не грузит", "не могу скачать", "не могу качать",
                "не работает", "не робит", "не ворк",
                "ошибка", "вирус", "трояны", "с троянами",
                "не дает скачать", "не запускается", "не запуск",
                "не могу его запустить", "не запескается",
                "не робит же", "не грузится",
                "немагу", "не магу", "немогу",
                "сайт не грузит", "не открывается",
                "не разрешает", "виндоус анти чит не дает",
                "у меня ошибка", "антивирус",
                "винда", "бяка", "не дает",
                "у меня не работает аник",
                "у меня не скачивается",
                "скок верям", "медленно кача",
                "у мя 800б", "у меня сыллка",
                "ссылка не открывается",
                "не могу понять как",
                "не выходит"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "виндоус", "антивирус", "анти чит", "бяка", "винда", "вирус", "трояны")) {
                    return pick(
                        "Ты отказываешься его скачивать?",
                        "Ты с офиц сайта скачал?",
                        "anydesk com",
                        "Все должно работать"
                    );
                }
                if (containsAny(lower, "аник не", "анидеск не")) {
                    if (!state.mentionedRudesk) {
                        state.mentionedRudesk = true;
                        return pick(
                            "Скачивай RuDeskTop",
                            "Качай рудеск",
                            "Газуй рудеск"
                        );
                    }
                    if (!state.mentionedRustdesk) {
                        state.mentionedRustdesk = true;
                        return pick(
                            "Скачивай RustDesk",
                            "Качай RustDesk"
                        );
                    }
                    return "Все должно работать";
                }
                if (!state.mentionedRudesk) {
                    state.mentionedRudesk = true;
                    return pick(
                        "Все должно работать",
                        "Скачивай RuDeskTop",
                        "Все должно скачиваться"
                    );
                }
                return pick(
                    "Все должно работать",
                    "Скачивай RustDesk",
                    "Все нормально должно работать"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 72: DON'T HAVE ANYDESK
        // ============================================================
        
        rules.add(new ResponseRule("no_anydesk", 72,
            (msg, lower, state, name) -> containsAny(lower,
                "нету аник", "нет аник", "нету ани деск", "нет ани деск",
                "у меня нету ани", "у меня нет ани",
                "аника нет", "анидеска нет",
                "нету такого", "нету его", "нет его",
                "у меня нету", "у меня его нет",
                "нету программы", "нет программы",
                "у меня нет никакой программ",
                "нету никакой программы",
                "просто нету аника",
                "тут анидеска нет"),
            (msg, lower, state, name) -> pick(
                "Скачивай",
                "Скачивай анидеск",
                "Cкачивай",
                "Качай"
            )
        ));
        
        // ============================================================
        // PRIORITY 71: RUDESK OFFER / QUESTIONS
        // ============================================================
        
        rules.add(new ResponseRule("rudesk_question", 71,
            (msg, lower, state, name) -> containsAny(lower,
                "рудеск", "rudesk", "rudesktop", "рудесктоп",
                "рудекс", "рудекстор", "рудескоп",
                "можно по рудеск", "рудеск сойдет",
                "а рудеск не подойдет",
                "из ру деск"),
            (msg, lower, state, name) -> {
                state.mentionedRudesk = true;
                if (containsAny(lower, "можно", "подойдет", "сойдет")) {
                    return pick("+", "Газуй", "Да", "+");
                }
                if (looksLikeRudeskCode(msg.replaceAll("[^\\d]", ""))) {
                    return pick("Принимай", "+", "Грузит");
                }
                return pick("+", "Газуй", "Скачивай");
            }
        ));
        
        // ============================================================
        // PRIORITY 70: RUSTDESK OFFER / QUESTIONS
        // ============================================================
        
        rules.add(new ResponseRule("rustdesk_question", 70,
            (msg, lower, state, name) -> containsAny(lower,
                "растдеск", "растдекс", "раст деск", "раст декс",
                "rustdesk", "rust desk", "rust деск",
                "растдескт"),
            (msg, lower, state, name) -> {
                state.mentionedRustdesk = true;
                if (containsAny(lower, "можно", "подойдет", "сойдет", "могу")) {
                    return pick("+", "Да");
                }
                return pick("+", "Скачивай");
            }
        ));
        
        // ============================================================
        // PRIORITY 69: ASKING WHERE TO DOWNLOAD
        // ============================================================
        
        rules.add(new ResponseRule("where_download", 69,
            (msg, lower, state, name) -> containsAny(lower,
                "где скачать", "как скачать", "откуда скачат",
                "как скачивать", "хз как скачать",
                "с какого сайта", "какая ссылка", "какая сылка",
                "я захожу на сыллку", "а как скачать",
                "скинь ссылку", "ссылку кинь",
                "где его найти", "где этот",
                "в каком магазине", "откуда",
                "что скачать", "что качать",
                "а что надо скачать", "что надо скачать",
                "название ани деск", "это где",
                "в плей маркете", "в плеймаркете",
                "в гугл плей",
                "а где код", "где код найти"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "код", "где код")) {
                    return pick(
                        "При запуске сразу будет",
                        "Прямо на самом видном месте",
                        "Открываешь и смотришь"
                    );
                }
                if (containsAny(lower, "название")) {
                    return "Да";
                }
                return pick(
                    "anydesk com",
                    "В гугле пиши анидеск",
                    "Инструкция в чате",
                    "Инструкция в лс",
                    "В браузере пиши anydesk com",
                    "Все инструкции в лс"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 68: PHONE PLAYER
        // ============================================================
        
        rules.add(new ResponseRule("phone_player", 68,
            (msg, lower, state, name) -> containsAny(lower,
                "я с телефон", "с телефона", "на телефоне",
                "я на тел", "у меня тел",
                "с мобильн", "на мобильн",
                "на андроид", "на айфон",
                "у меня телефон"),
            (msg, lower, state, name) -> pick(
                "Скачивай аник на телефон",
                "Скачивай анидеск на телефон",
                "Вообще не волнует",
                "Скачивай аник на телефон"
            )
        ));
        
        // ============================================================
        // PRIORITY 67: WHAT TO DO NEXT / INSTRUCTIONS
        // ============================================================
        
        rules.add(new ResponseRule("what_next", 67,
            (msg, lower, state, name) -> containsAny(lower,
                "что дальше", "дальше что", "что делать дальше",
                "чё дальше", "и что дальше", "ну и что",
                "и дальше", "что делать", "чё делать",
                "что мне делать", "чо делать", "чё делаит",
                "что дклать", "что скидывать",
                "что нужно делать", "как мне пройти",
                "так что сделать", "что сделать",
                "как пройти", "покажи как",
                "куда жмать", "куда тыкать",
                "куда там заходить", "как анидеском пользоват",
                "как пользоват", "я не понимаю что делать",
                "я не понииаю", "угу дальше",
                "чё делать то", "что мне надо делать"),
            (msg, lower, state, name) -> {
                if (state.gaveCodes) {
                    return pick("Принимай", "Принять нажми");
                }
                if (state.askedForAnydesk) {
                    return pick(
                        "Кидай код",
                        "Кидай длинный код",
                        "Скидывай код",
                        "Открывай аник и кидай код"
                    );
                }
                return pick(
                    "Скачивай анидеск",
                    "Все инструкции в чате",
                    "Инструкция в лс",
                    "Вся инструкция в чате"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 66: HOW MUCH TIME LEFT
        // ============================================================
        
        rules.add(new ResponseRule("time_left", 66,
            (msg, lower, state, name) -> containsAny(lower,
                "скок времени", "сколько времени", "скок время",
                "скок минут", "сколько минут", "скок у меня",
                "сколько у меня", "сколько ещё", "скок ещё",
                "сколько еще", "скок еще", "сколько осталось",
                "скок осталось", "скок верям", "еше",
                "скок врем", "сколько врем",
                "а сколько времени", "время ест",
                "доп время", "доп можно",
                "продли время", "дай время",
                "можно доп", "можно доп время",
                "можно подождать"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "доп", "продли", "можно подождать")) {
                    return pick("-", "Нет", "-");
                }
                int remaining = state.getRemainingMinutes();
                return pick(
                    remaining + " min",
                    remaining + " минут",
                    remaining + " мин"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 65: "WAIT" / "SECOND" / "HOLD ON"
        // ============================================================
        
        rules.add(new ResponseRule("wait_request", 65,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return containsAny(trimmed,
                    "щас", "ща ", "щя", "щяс", "секу", "секунд",
                    "сек ", "стой", "подожд", "погод", "чуть чуть",
                    "жди", "ждите", "жду",
                    "секу", "ща сек", "шас", "шаас",
                    "щаща", "ща ща", "щас сек",
                    "минут", "минуту", "ок щас",
                    "ща скач", "ща кач", "щас скач",
                    "подожди") || trimmed.equals("ща") || trimmed.equals("щас") ||
                    trimmed.equals("сек") || trimmed.equals("секу");
            },
            (msg, lower, state, name) -> {
                int remaining = state.getRemainingMinutes();
                return pick(
                    "Жду",
                    remaining + " минут",
                    "Жду",
                    "+",
                    "Давай"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 64: ASKING ABOUT CONFESSION / WHAT IS CONFESSION
        // ============================================================
        
        rules.add(new ResponseRule("confession_question", 64,
            (msg, lower, state, name) -> containsAny(lower,
                "какое признание", "признание в чем",
                "в чем признание", "что за признание",
                "признание это", "это что за призн",
                "какое", "на скок меньше",
                "на сколько забаните", "сколько бан",
                "на сколько бан", "а скок целый бан",
                "скок бан"),
            (msg, lower, state, name) -> {
                state.offeredConfession = true;
                if (containsAny(lower, "какое")) {
                    return "Признание в читах";
                }
                if (containsAny(lower, "на скок", "на сколько", "скок бан", "скок целый")) {
                    return pick(
                        "Признание 20 дней, отказ 30 дней",
                        "30 дней",
                        "признание 20 дней",
                        "на 10 дней"
                    );
                }
                return "Признание в читах";
            }
        ));
        
        // ============================================================
        // PRIORITY 63: ACCEPT/ACCEPT BUTTON
        // ============================================================
        
        rules.add(new ResponseRule("accept_related", 63,
            (msg, lower, state, name) -> containsAny(lower,
                "принял", "я принял", "как принять",
                "как приня", "приинимать", "принимаю",
                "нажал принять", "кнопку принять",
                "у меня нет кнопки",
                "мне не пришло", "от имени",
                "от кого принимать"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "как принять", "приинимать", "нет кнопки")) {
                    return pick(
                        "Нажми кнопку принять",
                        "Нажать кнопку принять",
                        "Кнопка принять"
                    );
                }
                if (containsAny(lower, "от имени", "от кого")) {
                    return "Любой";
                }
                if (containsAny(lower, "не пришло")) {
                    return "Принимай";
                }
                return pick("+", "Пред 1/3 не трогай мышку");
            }
        ));
        
        // ============================================================
        // PRIORITY 62: REGISTRATION QUESTION
        // ============================================================
        
        rules.add(new ResponseRule("registration", 62,
            (msg, lower, state, name) -> containsAny(lower,
                "регаюсь", "регаться", "регистрац",
                "там регаться", "зарегаю", "зарегистри",
                "щя зарегаюсь", "я зарегируюсь",
                "надо регаться", "регистрировать"),
            (msg, lower, state, name) -> pick(
                "Не надо там регаться",
                "Нет",
                "Не надо регаться"
            )
        ));
        
        // ============================================================
        // PRIORITY 61: MINIMAP / ALLOWED MODS
        // ============================================================
        
        rules.add(new ResponseRule("minimap", 61,
            (msg, lower, state, name) -> containsAny(lower,
                "миникарта", "мини карта", "минимап",
                "карта разреш", "разрешен мод",
                "пульс это же не софт", "пульс это софт"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "пульс")) {
                    if (containsAny(lower, "офиц")) {
                        return "Не не софт";
                    }
                    return "Смотря какой";
                }
                return pick("+", "Да", "Принимай");
            }
        ));
        
        // ============================================================
        // PRIORITY 60: REPORTING OTHER PLAYERS
        // ============================================================
        
        rules.add(new ResponseRule("report_player", 60,
            (msg, lower, state, name) -> containsAny(lower,
                "тут один читер", "тут читер", "есть читер",
                "могу дать его ник", "против меня софтер",
                "могу ник", "его ник", "вот ник",
                "кидайте прову", "кидай прову",
                "стажеры с софтом", "стажёры с софтом",
                "у него рп который",
                "на него реп",
                "напиши /cr"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "могу дать", "могу ник", "дать ник")) {
                    return "Давай";
                }
                if (containsAny(lower, "стажер", "стажёр")) {
                    return "Примем меры";
                }
                if (containsAny(lower, "напиши /cr")) {
                    return "Напиши /cr nik";
                }
                return pick(
                    "Давай",
                    "Напиши /cr nik",
                    "Примем меры"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 59: ITEMS / RESOURCES / MONEY REQUESTS
        // ============================================================
        
        rules.add(new ResponseRule("resource_requests", 59,
            (msg, lower, state, name) -> containsAny(lower,
                "можно ресы", "ресы раздам", "можно сложити",
                "можно пеперони", "можно груз",
                "можно баблко кинуть", "деньги отдам",
                "можно кинуть",
                "дай денег", "тимейту деньги",
                "дам сетку", "сколько дашь",
                "стой а можно я просто если забанят",
                "дать что то"),
            (msg, lower, state, name) -> pick("-", "Неа", "-")
        ));
        
        // ============================================================
        // PRIORITY 58: LEGAL CONCERNS
        // ============================================================
        
        rules.add(new ResponseRule("legal_concerns", 58,
            (msg, lower, state, name) -> containsAny(lower,
                "не законно", "незаконно", "незаконо",
                "переживаю за свой", "вдруг вы что",
                "чтото с ним сделаете", "не доверяю",
                "анидеску не доверяю", "родительский контроль"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "не довер")) {
                    return pick(
                        "1.Заходя на сервер вы соглашаетесь с правилами и при проверке вы обязаны предоставить анидеск или другую программу",
                        "Отказ?"
                    );
                }
                if (containsAny(lower, "незакон", "не закон")) {
                    return pick(
                        "1.Заходя на сервер вы соглашаетесь с правилами и при проверке вы обязаны предоставить анидеск или другую программу",
                        "2. При несогласии с действиями модератора вы закрываете доступ и пишите в группу кураторов",
                        "Отказ?"
                    );
                }
                if (containsAny(lower, "родительский")) {
                    return pick(
                        "Скачивай анидеск проси разрешения",
                        "А как мы тогда тебя проверим"
                    );
                }
                return "Отказ?";
            }
        ));
        
        // ============================================================
        // PRIORITY 57: FROM RUSSIA (ANYDESK ISSUES)
        // ============================================================
        
        rules.add(new ResponseRule("from_russia", 57,
            (msg, lower, state, name) -> containsAny(lower,
                "я из рф", "я с рф", "из рф", "с рф",
                "из россии", "с россии", "из рф аник",
                "он в рф работае", "аник не ворк"),
            (msg, lower, state, name) -> {
                state.mentionedRudesk = true;
                return pick(
                    "Скачивай RuDeskTop",
                    "Скачивай рудеск",
                    "Cкачивай RuDeskTop даю 7 минут",
                    "Скачивай RuDeskTop или запускай впн на пк и с впн аник включай"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 56: VPN ISSUES
        // ============================================================
        
        rules.add(new ResponseRule("vpn_issues", 56,
            (msg, lower, state, name) -> containsAny(lower,
                "впн", "vpn",
                "если впн включу", "впн тоже нет",
                "кикнет с сервера", "кикнет"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "кикнет", "если впн включу")) {
                    return pick(
                        "Скачивай RuDeskTop значит",
                        "Скачивай RuDeskTop"
                    );
                }
                if (containsAny(lower, "нет", "нету")) {
                    return "Скачивай RuDeskTop";
                }
                return pick(
                    "Скачивай RuDeskTop",
                    "Скачивай RuDeskTop или запускай впн на пк и с впн аник включай"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 55: PREVIOUSLY CHECKED
        // ============================================================
        
        rules.add(new ResponseRule("previously_checked", 55,
            (msg, lower, state, name) -> containsAny(lower,
                "меня проверяли", "уже проверяли", "вчера проверял",
                "сегодня проверял", "проверяли сегодня",
                "1 раз на прове", "первый раз",
                "я вчера прову проходил",
                "могу рек кинуть где меня"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "сегодня")) {
                    return pick(
                        "Я тебя еще раз проверю",
                        "Ща 5 сек проверю"
                    );
                }
                if (containsAny(lower, "вчера")) {
                    return "Обманывать не хорошо";
                }
                return pick(
                    "Аник жду",
                    "Давай аник",
                    "Скачивай аник"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 54: PAID/FREE QUESTION
        // ============================================================
        
        rules.add(new ResponseRule("paid_question", 54,
            (msg, lower, state, name) -> containsAny(lower,
                "платная", "платный", "платно",
                "евро надо купить", "мне пишет евро",
                "бесплатн", "фри", "для дома",
                "расширеная версия", "расширенная"),
            (msg, lower, state, name) -> pick(
                "Она не платная",
                "Он бесплатный",
                "Он не платный",
                "Заходишь на сайт anydesk com для домашнего использования"
            )
        ));
        
        // ============================================================
        // PRIORITY 53: "WIPE" QUESTIONS
        // ============================================================
        
        rules.add(new ResponseRule("wipe_question", 53,
            (msg, lower, state, name) -> containsAny(lower,
                "когда вайп", "до вайпа", "вайп"),
            (msg, lower, state, name) -> "20 дней +- точно не могу сказать"
        ));
        
        // ============================================================
        // PRIORITY 52: ASKING CONFIRMATION QUESTIONS
        // ============================================================
        
        rules.add(new ResponseRule("confirmation", 52,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.equals("да") || trimmed.equals("да?") ||
                       trimmed.equals("+") || trimmed.equals("ок") ||
                       trimmed.equals("окей") || trimmed.equals("ладно") ||
                       trimmed.equals("хорошо") || trimmed.equals("понял") ||
                       trimmed.equals("пон") || trimmed.equals("угу") ||
                       trimmed.equals("ну") || trimmed.equals("ага") ||
                       trimmed.equals("da") || trimmed.equals("ladno") ||
                       trimmed.equals("ну ок") || trimmed.equals("тогда ок") ||
                       trimmed.equals("тогд ок");
            },
            (msg, lower, state, name) -> {
                if (!state.askedForAnydesk) {
                    state.askedForAnydesk = true;
                    int remaining = state.getRemainingMinutes();
                    return remaining + " min у тебя";
                }
                if (state.gaveCodes) {
                    return pick("Принимай", "+");
                }
                return pick(
                    "Жду",
                    "+",
                    "Давай",
                    "Скачивай"
                );
            }
        ));

        // ============================================================
        // PRIORITY 51: QUESTION MARKS ONLY
        // ============================================================
        
        rules.add(new ResponseRule("question_marks", 51,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.matches("\\?+") || trimmed.equals("??") || trimmed.equals("???") ||
                       trimmed.equals("?????") || trimmed.equals("??????") || trimmed.equals("?????7");
            },
            (msg, lower, state, name) -> {
                if (state.messageCount <= 2) {
                    return "Проверка";
                }
                if (!state.askedForAnydesk) {
                    return "Скачивай аник";
                }
                return pick(
                    "Аник жду",
                    "Жду",
                    "+",
                    "Скачивай аник"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 50: SINGLE WORDS / SHORT RESPONSES
        // ============================================================
        
        rules.add(new ResponseRule("short_responses", 50,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.equals("аник") || trimmed.equals("аник?") ||
                       trimmed.equals("анидеск") || trimmed.equals("кидай") ||
                       trimmed.equals("ну") || trimmed.equals("ну че") ||
                       trimmed.equals("ну чо") || trimmed.equals("ну что") ||
                       trimmed.equals("го") || trimmed.equals("go") ||
                       trimmed.equals("вот") || trimmed.equals("на") ||
                       trimmed.equals("нуу") || trimmed.equals("ну?") ||
                       trimmed.equals("это?") || trimmed.equals("это.?") ||
                       trimmed.equals("ale") || trimmed.equals("ало") ||
                       trimmed.equals("ау") || trimmed.equals("аууу") ||
                       trimmed.equals("ауу") || trimmed.equals("модер") ||
                       trimmed.equals("ты тут") || trimmed.equals("ты тут?") ||
                       trimmed.equals("ты здесь") || trimmed.equals("ты здесь?");
            },
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                if (containsAny(trimmed, "аник")) {
                    return pick("+", "Да", "Жду код");
                }
                if (containsAny(trimmed, "кидай")) {
                    return pick(
                        "Ты из рф?",
                        "Скачивай",
                        "Кидай код"
                    );
                }
                if (containsAny(trimmed, "вот", "на", "это")) {
                    return pick("+", "Принимай");
                }
                if (containsAny(trimmed, "ты тут", "ты здесь", "ало", "ау", "модер")) {
                    return pick(
                        "Да да я тут",
                        "+",
                        "Есть",
                        "Я тут"
                    );
                }
                if (containsAny(trimmed, "ну", "го", "go")) {
                    return pick(
                        "Жду аник",
                        "Аник жду",
                        "+",
                        "Анидеск жду"
                    );
                }
                return pick("Жду аник", "+");
            }
        ));
        
        // ============================================================
        // PRIORITY 49: KEYBOARD MASH / RANDOM LETTERS
        // ============================================================
        
        rules.add(new ResponseRule("keyboard_mash", 49,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.matches("[а-яa-z]{1,3}") && 
                       !containsAny(trimmed, "да", "ок", "не", "ку", "нет", "бб") ||
                       trimmed.equals("of") || trimmed.equals("er") || trimmed.equals("п") ||
                       trimmed.equals("гл") || trimmed.equals("пр") ||
                       containsAny(trimmed, "ыыыы", "ааааа", "ууууу", "еееее",
                           "зажпжапж", "окак");
            },
            (msg, lower, state, name) -> pick(
                "Аник жду",
                "Жду",
                "Скачивай аник"
            )
        ));
        
        // ============================================================
        // PRIORITY 48: WEAK PC / SLOW INTERNET
        // ============================================================
        
        rules.add(new ResponseRule("weak_pc", 48,
            (msg, lower, state, name) -> containsAny(lower,
                "у меня пк слаб", "пк слабый", "комп слаб",
                "интернет слаб", "инет слаб", "инет говно",
                "2кб в секунд", "800б в сек",
                "пк за 15к", "пк на 15к",
                "долго скачива", "микроволновк",
                "у меня черный экран", "лагает",
                "танки скачиваются", "пк лагает",
                "медленно качается"),
            (msg, lower, state, name) -> {
                int remaining = state.getRemainingMinutes();
                if (containsAny(lower, "15к", "слаб")) {
                    return pick(
                        "Жду еще минута у тебя",
                        remaining + " минут",
                        "Скачивай"
                    );
                }
                return pick(
                    remaining + " минут",
                    "Скачивай",
                    "Жду"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 47: INTERNET CAFE / SCHOOL COMPUTER
        // ============================================================
        
        rules.add(new ResponseRule("public_computer", 47,
            (msg, lower, state, name) -> containsAny(lower,
                "в компах", "в компьютерном", "в клубе",
                "в компьютерн", "нельзя скачивать",
                "не дает ничего скачать", "тут нельзя",
                "с компа", "брата"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "нельзя скачив", "не дает")) {
                    return pick(
                        "Зови админа аник обычно на таких компах есть",
                        "Тг есть?"
                    );
                }
                return "Вообще не волнует";
            }
        ));
        
        // ============================================================
        // PRIORITY 46: EMOJI / EMOTIONAL RESPONSES
        // ============================================================
        
        rules.add(new ResponseRule("emotional", 46,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.equals(")") || trimmed.equals(")))") ||
                       trimmed.equals("(") || trimmed.equals("((((") ||
                       trimmed.matches("[)(:]+") ||
                       trimmed.equals("хаха") || trimmed.equals("хахаха") ||
                       trimmed.equals("ахахах") || trimmed.equals("xd") ||
                       trimmed.equals("найс") || trimmed.equals("nais") ||
                       trimmed.equals("круто") || trimmed.equals("прикольно") ||
                       trimmed.equals("гг") || trimmed.equals("gg") ||
                       trimmed.equals("лол") || trimmed.equals("lol");
            },
            (msg, lower, state, name) -> {
                if (containsAny(lower, "хаха", "ахах", "xd")) {
                    return pick("После проверки)", "Аник жду");
                }
                if (containsAny(lower, ")")) {
                    return "Признание уменьшает срок на 35%";
                }
                if (containsAny(lower, "(")) {
                    return pick(
                        "Признание уменьшает бан на 35%",
                        "Скачивай аник"
                    );
                }
                return pick("Аник жду", "+");
            }
        ));
        
        // ============================================================
        // PRIORITY 45: STALLING / TALKING NONSENSE
        // ============================================================
        
        rules.add(new ResponseRule("stalling", 45,
            (msg, lower, state, name) -> containsAny(lower,
                "я в дубае", "я тоже", "расказу", "поговорим",
                "пока прову", "дам сетку", "а можно пеперони",
                "еще 1 тип", "забаниш я ночь",
                "в подушку плакать", "мне пизда",
                "мой пк за", "я девка",
                "я арсений", "по приказу",
                "я твой организм", "а когда вайп",
                "4 твинк"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "мне пизда")) {
                    state.offeredConfession = true;
                    return "Признание уменьшает бан на 35%";
                }
                if (containsAny(lower, "подушку плакать", "забаниш я")) {
                    return "Адекватно веди себя";
                }
                return pick(
                    "Аник жду",
                    "Бро не тяни время",
                    "Аник или признание",
                    "Жду аник"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 44: OFFER CONFESSION (proactive)
        // ============================================================
        
        rules.add(new ResponseRule("offer_confession", 44,
            (msg, lower, state, name) -> {
                // After some messages, offer confession
                return state.messageCount > 5 && !state.offeredConfession && 
                       state.getElapsedMinutes() >= 3;
            },
            (msg, lower, state, name) -> {
                state.offeredConfession = true;
                return pick(
                    "Признание уменьшает наказание на 35%",
                    "Признание уменьшает срок бана на 35%",
                    "Давай что бы время не тратить ты признаешься и я забаню на 35% меньше",
                    "Признание уменьшает бан на 35%"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 43: GENERAL "NO" RESPONSES
        // ============================================================
        
        rules.add(new ResponseRule("no_response", 43,
            (msg, lower, state, name) -> {
                String trimmed = lower.trim();
                return trimmed.equals("нет") || trimmed.equals("не") ||
                       trimmed.equals("неа") || trimmed.equals("нее") ||
                       trimmed.equals("нееееееее") || trimmed.startsWith("нееее");
            },
            (msg, lower, state, name) -> pick(
                "Тогда жду аник",
                "Скачивай аник",
                "Аник жду",
                "Скачивай анидеск"
            )
        ));
        
        // ============================================================
        // PRIORITY 42: PASSWORD MENTIONS
        // ============================================================
        
        rules.add(new ResponseRule("password", 42,
            (msg, lower, state, name) -> containsAny(lower,
                "пароль", "пароль ", "пороль",
                "password"),
            (msg, lower, state, name) -> null // ignore or ban based on context
        ));
        
        // ============================================================
        // PRIORITY 40: ENGLISH/TRANSLITERATION
        // ============================================================
        
        rules.add(new ResponseRule("transliteration", 40,
            (msg, lower, state, name) -> containsAny(lower,
                "vse bani", "da ", "ladno", "togda",
                "kiday", "na", "ono",
                "i skacat ne mogy"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "vse bani", "da")) {
                    if (containsAny(lower, "bani")) return null; // confession/ban
                    return pick("Жду", "+");
                }
                if (containsAny(lower, "i skacat ne mogy")) {
                    return "Ты сможешь я в тебя верю";
                }
                if (containsAny(lower, "ladno", "togda")) {
                    return pick("Жду", "Скачивай");
                }
                return pick("Жду", "Скачивай аник");
            }
        ));
        
        // ============================================================
        // PRIORITY 38: CLIENT OFFLINE / CONNECTION ISSUES
        // ============================================================
        
        rules.add(new ResponseRule("connection_issues", 38,
            (msg, lower, state, name) -> containsAny(lower,
                "клиент не в сети", "не подключается",
                "соединение заверш", "не воркает",
                "не ворк", "ошибка подключ",
                "кинь еще раз", "попробуй еще раз",
                "проверяй"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "кинь еще", "попробуй еще")) {
                    return pick(
                        "Скачивай RustDesk",
                        "Переприми"
                    );
                }
                return pick(
                    "Скачивай RustDesk",
                    "Переустанови аник",
                    "Ошибка подключения"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 35: ALL IN ENGLISH / CANT TYPE RUSSIAN
        // ============================================================
        
        rules.add(new ResponseRule("english_text", 35,
            (msg, lower, state, name) -> containsAny(lower,
                "у меня всё на англ", "у меня все на англ",
                "на английском", "на англиском"),
            (msg, lower, state, name) -> {
                int remaining = state.getRemainingMinutes();
                return "У тебя " + remaining + " минут осталось";
            }
        ));
        
        // ============================================================
        // PRIORITY 30: PLUGIN AD1 / PHONE SPECIFIC
        // ============================================================
        
        rules.add(new ResponseRule("plugin_ad1", 30,
            (msg, lower, state, name) -> containsAny(lower,
                "плагин", "plugin", "ad1",
                "активир", "три линии", "три точки",
                "полный доступ", "профиль прав",
                "права досутпа", "права доступа"),
            (msg, lower, state, name) -> "нажать слева сверху на три линии в anydesk, настройкА --> Плагин AD1 --> Активировать! --> в настройках ищете плагин и включаете его, разрешив контролировать устройство, после чего возвращайтесь к плагину во вкладке \"настройкА\" и соглашаетесь на его использование"
        ));
        
        // ============================================================
        // PRIORITY 25: AFTER CHECK / ALL DONE
        // ============================================================
        
        rules.add(new ResponseRule("check_done", 25,
            (msg, lower, state, name) -> containsAny(lower,
                "я прошел", "я прошёл", "все?", "всё?",
                "спасибо", "спс", "сенкс", "thanks",
                "спасибо огромное"),
            (msg, lower, state, name) -> {
                if (containsAny(lower, "спасибо", "спс")) {
                    return pick(
                        "Рад помочь",
                        "Пред 1/3 не трогай мышку"
                    );
                }
                return pick(
                    "Пред 1/3 не трогай мышку",
                    "+",
                    "Молодец"
                );
            }
        ));
        
        // ============================================================
        // PRIORITY 20: OBEDIENCE / TRYING
        // ============================================================
        
        rules.add(new ResponseRule("trying", 20,
            (msg, lower, state, name) -> containsAny(lower,
                "попробую", "постараюсь", "ладно постараюсь",
                "я тут", "я тоже", "я готов",
                "запускаю", "открыл", "открываю",
                "ладно", "ладна", "хорошо",
                "ок ща", "ок щас", "лан"),
            (msg, lower, state, name) -> {
                int remaining = state.getRemainingMinutes();
                return pick(
                    "Жду",
                    remaining + " минут",
                    "+",
                    "Давай"
                );
            }
        ));

        // ============================================================
        // PRIORITY 15: CATCH-ALL FOR UNKNOWN MESSAGES
        // ============================================================
        
        rules.add(new ResponseRule("catchall", 15,
            (msg, lower, state, name) -> true,
            (msg, lower, state, name) -> {
                if (state.messageCount <= 1) {
                    return "Это проверка на читы, у Вас есть 7 мин. чтобы скинуть AnyDesk и пройти проверку! Признание уменьшает наказание! В случае отказа/выхода/игнора - Бан без перепроверки!";
                }
                if (state.messageCount > 8 && !state.offeredConfession) {
                    state.offeredConfession = true;
                    return "Признание уменьшает наказание на 35%";
                }
                return pick(
                    "Аник жду",
                    "Скачивай аник",
                    "Жду анидеск",
                    "Анидеск жду"
                );
            }
        ));

        // Sort rules by priority (highest first)
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
    }

    // ======================== MAIN RESPONSE METHOD ========================
    
    public String getResponse(String playerMessage, String playerName) {
        if (playerMessage == null || playerMessage.trim().isEmpty()) return null;

        String lower = playerMessage.toLowerCase().trim();
        
        // Get or create player state
        PlayerState state = playerStates.computeIfAbsent(playerName, k -> new PlayerState());
        state.messageCount++;
        state.lastMessageTime = System.currentTimeMillis();
        state.recentMessages.add(playerMessage);
        
        // Keep only last 20 messages
        if (state.recentMessages.size() > 20) {
            state.recentMessages.remove(0);
        }

        // Find matching rule
        for (ResponseRule rule : rules) {
            try {
                if (rule.matcher.matches(playerMessage, lower, state, playerName)) {
                    String response = rule.generator.generate(playerMessage, lower, state, playerName);
                    state.lastResponseCategory = rule.category;
                    
                    if (response == null) {
                        // null means "ban" - don't auto-respond, let moderator handle
                        // Or we could send a predefined ban message
                        HolyWorldAutoReply.LOGGER.info("[AutoReply] BAN signal for player {} (category: {}): {}", 
                            playerName, rule.category, playerMessage);
                        return null; // Don't auto-respond to ban situations
                    }
                    
                    HolyWorldAutoReply.LOGGER.info("[AutoReply] Matched rule '{}' for {}: {} -> {}", 
                        rule.category, playerName, playerMessage, response);
                    return response;
                }
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AutoReply] Error in rule '{}': {}", rule.category, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Clear player state (call when check ends)
     */
    public void clearPlayerState(String playerName) {
        playerStates.remove(playerName);
    }

    /**
     * Clear all states
     */
    public void clearAllStates() {
        playerStates.clear();
    }
}
