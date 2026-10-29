package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Morning and evening adhkar for the idle screen (Hisn al-Muslim selection). Arabic as narrated; the English lines
 * are plain renderings of the meaning written for this screen. Shown from 04:00 to 10:00 (morning) and 15:00 to
 * 19:30 (evening), alternating with the Qur'an verses and hadith.
 */
final class Adhkar {
    private Adhkar() {}

    /** {arabic, english, reference, when: "m" morning, "e" evening, "b" both} */
    private static final String[][] ALL = {
            {"أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
                    "We have entered the morning, and all dominion this morning belongs to Allah. All praise is for Allah. There is no god but Allah alone, without partner; His is the dominion and His the praise, and He has power over all things.",
                    "Sahih Muslim", "m"},
            {"أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
                    "We have entered the evening, and all dominion this evening belongs to Allah. All praise is for Allah. There is no god but Allah alone, without partner; His is the dominion and His the praise, and He has power over all things.",
                    "Sahih Muslim", "e"},
            {"اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ",
                    "O Allah, by You we enter the morning and by You we enter the evening; by You we live and by You we die, and to You is the rising.",
                    "Jami' at-Tirmidhi", "m"},
            {"اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
                    "O Allah, by You we enter the evening and by You we enter the morning; by You we live and by You we die, and to You is the final return.",
                    "Jami' at-Tirmidhi", "e"},
            {"بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
                    "In the name of Allah, with whose name nothing on earth or in the heavens can cause harm; He is the All-Hearing, the All-Knowing.",
                    "Abu Dawud, Tirmidhi · three times", "b"},
            {"سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                    "Glory be to Allah, and all praise is His.",
                    "Sahih Muslim · one hundred times", "b"},
            {"حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ، عَلَيْهِ تَوَكَّلْتُ، وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
                    "Allah is enough for me; there is no god but Him. In Him I put my trust, and He is the Lord of the Mighty Throne.",
                    "Abu Dawud · seven times", "b"},
            {"أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
                    "I seek refuge in the perfect words of Allah from the evil of what He has created.",
                    "Sahih Muslim · three times", "e"},
            {"رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا",
                    "I am pleased with Allah as my Lord, with Islam as my way of life, and with Muhammad ﷺ as my Prophet.",
                    "Abu Dawud · three times", "b"},
            {"اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَىٰ عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
                    "O Allah, You are my Lord; there is no god but You. You created me and I am Your servant, and I hold to Your covenant and promise as far as I can. I seek refuge in You from the evil of what I have done. I acknowledge Your favour upon me and I acknowledge my sin, so forgive me, for none forgives sins but You.",
                    "Sahih al-Bukhari · Sayyid al-Istighfar", "b"},
            {"اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَٰهَ إِلَّا أَنْتَ",
                    "O Allah, keep my body well; O Allah, keep my hearing well; O Allah, keep my sight well. There is no god but You.",
                    "Abu Dawud · three times", "b"},
            {"يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَىٰ نَفْسِي طَرْفَةَ عَيْنٍ",
                    "O Ever-Living, O Sustainer of all, by Your mercy I call for help: set right all my affairs, and do not leave me to myself even for the blink of an eye.",
                    "An-Nasa'i, al-Hakim", "b"},
    };

    /** "m" in the morning window, "e" in the evening window, null otherwise. */
    static String window() {
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (m >= 4 * 60 && m < 10 * 60) return "m";
        if (m >= 15 * 60 && m < 19 * 60 + 30) return "e";
        return null;
    }

    /** The {@code n}th dhikr of the window (wrapping), as an idle-screen item. */
    static ReflectionSource.Item get(String window, int n) {
        List<String[]> list = new ArrayList<>();
        for (String[] a : ALL) if (a[3].equals("b") || a[3].equals(window)) list.add(a);
        String[] a = list.get(Math.floorMod(n, list.size()));
        String label = window.equals("m") ? "Morning adhkar" : "Evening adhkar";
        return new ReflectionSource.Item(false, a[0], a[1], a[2], label);
    }
}
