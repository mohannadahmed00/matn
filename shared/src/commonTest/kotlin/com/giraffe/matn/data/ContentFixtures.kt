package com.giraffe.matn

import com.giraffe.matn.testseed.SeedMatn

val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun parseSeed(raw: String): SeedMatn = json.decodeFromString(raw)

const val SIMPLE_MATN_JSON = """
{
  "id": "b3f1e2a4-0000-4000-8000-000000000001",
  "title": "الأجرومية",
  "author": "ابن آجُرُّوم",
  "description": "متن مختصر في علم النحو",
  "coverImageRef": "covers/ajurrumiyya.jpg",
  "structureKind": "SIMPLE",
  "defaultReciterId": "reciter-default-v1",
  "verses": [
    {
      "id": "b3f1e2a4-0000-4000-8000-0000000000v1",
      "displayNumber": 1,
      "arabicText": "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
      "durationMs": 8200,
      "audio": {
        "id": "b3f1e2a4-0000-4000-8000-0000000000a1",
        "fileRef": "ajurrumiyya_verse_001.mp3",
        "durationMs": 8200
      }
    },
    {
      "id": "b3f1e2a4-0000-4000-8000-0000000000v2",
      "displayNumber": 2,
      "arabicText": "وَأَقسامُهُ ثَلاثَةٌ: اِسمٌ، وَفِعلٌ، وَحَرفٌ جاءَ لِمَعنىً",
      "durationMs": 7400,
      "audio": {
        "id": "b3f1e2a4-0000-4000-8000-0000000000a2",
        "fileRef": "ajurrumiyya_verse_002.mp3",
        "durationMs": 7400
      }
    },
    {
      "id": "b3f1e2a4-0000-4000-8000-0000000000v3",
      "displayNumber": 3,
      "arabicText": "فَالاسمُ يُعرَفُ بِالخَفضِ وَالتَنوينِ وَوُروجِ الأَلِفِ",
      "durationMs": 6900,
      "audio": {
        "id": "b3f1e2a4-0000-4000-8000-0000000000a3",
        "fileRef": "ajurrumiyya_verse_003.mp3",
        "durationMs": 6900
      }
    },
    {
      "id": "b3f1e2a4-0000-4000-8000-0000000000v4",
      "displayNumber": 4,
      "arabicText": "والفِعلُ يُعرَفُ بِقَد وَالسينِ وَسَوفَ وَتاءِ التَّأنيثِ",
      "durationMs": 8800,
      "audio": {
        "id": "b3f1e2a4-0000-4000-8000-0000000000a4",
        "fileRef": "ajurrumiyya_verse_004.mp3",
        "durationMs": 8800
      }
    }
  ]
}
""";

const val INVALID_DUPLICATE_ORDER_JSON = """
{
  "id": "c1a1a1a1-0000-4000-8000-000000000001",
  "title": "نموذج مكرر",
  "author": "مؤلف",
  "description": "اختبار تكرار الترتيب",
  "structureKind": "SIMPLE",
  "defaultReciterId": "reciter-default-v1",
  "verses": [
    {
      "id": "c1a1a1a1-0000-4000-8000-0000000000v1",
      "displayNumber": 1,
      "arabicText": "بيت أول",
      "durationMs": 1000,
      "audio": {
        "id": "c1a1a1a1-0000-4000-8000-0000000000a1",
        "fileRef": "dup_verse_001.mp3",
        "durationMs": 1000
      }
    },
    {
      "id": "c1a1a1a1-0000-4000-8000-0000000000v2",
      "displayNumber": 1,
      "arabicText": "بيت ثان مكرر الترتيب",
      "durationMs": 2000,
      "audio": {
        "id": "c1a1a1a1-0000-4000-8000-0000000000a2",
        "fileRef": "dup_verse_002.mp3",
        "durationMs": 2000
      }
    },
    {
      "id": "c1a1a1a1-0000-4000-8000-0000000000v3",
      "displayNumber": 2,
      "arabicText": "بيت ثالث",
      "durationMs": 3000,
      "audio": {
        "id": "c1a1a1a1-0000-4000-8000-0000000000a3",
        "fileRef": "dup_verse_003.mp3",
        "durationMs": 3000
      }
    }
  ]
}
""";

const val INVALID_MISSING_AUDIO_JSON = """
{
  "id": "d2b2b2b2-0000-4000-8000-000000000001",
  "title": "نموذج ناقص الصوت",
  "author": "مؤلف",
  "description": "اختبار غياب الصوت",
  "structureKind": "SIMPLE",
  "defaultReciterId": "reciter-default-v1",
  "verses": [
    {
      "id": "d2b2b2b2-0000-4000-8000-0000000000v1",
      "displayNumber": 1,
      "arabicText": "بيت أول",
      "durationMs": 1000,
      "audio": {
        "id": "d2b2b2b2-0000-4000-8000-0000000000a1",
        "fileRef": "missing_verse_001.mp3",
        "durationMs": 1000
      }
    },
    {
      "id": "d2b2b2b2-0000-4000-8000-0000000000v2",
      "displayNumber": 2,
      "arabicText": "بيت ثان بلا صوت",
      "durationMs": 2000
    }
  ]
}
""";

const val STRUCTURED_MATN_JSON = """
{
  "id": "e5c5c5c5-0000-4000-8000-000000000001",
  "title": "متن الآجرومية مبوب",
  "author": "ابن آجُرُّوم",
  "description": "نموذج مبوب بأبواب",
  "structureKind": "STRUCTURED",
  "defaultReciterId": "reciter-default-v1",
  "chapters": [
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000c1",
      "title": "باب الكلام",
      "order": 1
    },
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000c2",
      "title": "باب الإعراب",
      "order": 2
    }
  ],
  "verses": [
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000v1",
      "chapterId": "e5c5c5c5-0000-4000-8000-0000000000c1",
      "displayNumber": 1,
      "arabicText": "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
      "durationMs": 8200,
      "audio": {
        "id": "e5c5c5c5-0000-4000-8000-0000000000a1",
        "fileRef": "structured_verse_001.mp3",
        "durationMs": 8200
      }
    },
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000v2",
      "chapterId": "e5c5c5c5-0000-4000-8000-0000000000c1",
      "displayNumber": 2,
      "arabicText": "وَأَقسامُهُ ثَلاثَةٌ: اِسمٌ، وَفِعلٌ، وَحَرفٌ",
      "durationMs": 7400,
      "audio": {
        "id": "e5c5c5c5-0000-4000-8000-0000000000a2",
        "fileRef": "structured_verse_002.mp3",
        "durationMs": 7400
      }
    },
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000v3",
      "chapterId": "e5c5c5c5-0000-4000-8000-0000000000c1",
      "displayNumber": 3,
      "arabicText": "فَالاسمُ يُعرَفُ بِالخَفضِ وَالتَنوينِ",
      "durationMs": 6900,
      "audio": {
        "id": "e5c5c5c5-0000-4000-8000-0000000000a3",
        "fileRef": "structured_verse_003.mp3",
        "durationMs": 6900
      }
    },
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000v4",
      "chapterId": "e5c5c5c5-0000-4000-8000-0000000000c2",
      "displayNumber": 4,
      "arabicText": "الإِعرابُ هُوَ تَغييرُ أَواخِرِ الكَلِماتِ",
      "durationMs": 8100,
      "audio": {
        "id": "e5c5c5c5-0000-4000-8000-0000000000a4",
        "fileRef": "structured_verse_004.mp3",
        "durationMs": 8100
      }
    },
    {
      "id": "e5c5c5c5-0000-4000-8000-0000000000v5",
      "chapterId": "e5c5c5c5-0000-4000-8000-0000000000c2",
      "displayNumber": 5,
      "arabicText": "وَأَقسامُهُ أَربَعَةٌ: رَفعٌ وَنَصبٌ وَخَفضٌ وَجَزْمٌ",
      "durationMs": 9300,
      "audio": {
        "id": "e5c5c5c5-0000-4000-8000-0000000000a5",
        "fileRef": "structured_verse_005.mp3",
        "durationMs": 9300
      }
    }
  ]
}
""";