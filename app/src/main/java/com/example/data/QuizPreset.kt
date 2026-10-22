package com.example.data

data class QuizPreset(
    val id: String,
    val title: String,
    val subject: String,
    val language: String,
    val questionPrompt: String
)

object QuizPresets {
    val items = listOf(
        QuizPreset(
            id = "arabic_physics",
            title = "وحدة قياس القوة (فيزياء)",
            subject = "Science / علوم",
            language = "العربية",
            questionPrompt = "ما هي وحدة قياس القوة في النظام الدولي للوحدات (SI)؟\n(أ) جول\n(ب) نيوتن\n(ج) باسكال\n(د) واط"
        ),
        QuizPreset(
            id = "math_algebra",
            title = "Linear Equation / معادلة جبرية",
            subject = "Math / رياضيات",
            language = "English",
            questionPrompt = "Solve for x in the equation:\n4x - 9 = 27\nA) x = 7\nB) x = 9\nC) x = 8\nD) x = 6"
        ),
        QuizPreset(
            id = "arabic_history",
            title = "تاريخ إسلامي (العاصمة الأولى)",
            subject = "Trivia / تاريخ",
            language = "العربية",
            questionPrompt = "ما هي عاصمة الدولة العباسية الأولى قبل تأسيس وبناء مدينة بغداد؟\n(أ) دمشق\n(ب) الكوفة\n(ج) البصرة\n(د) سامراء"
        ),
        QuizPreset(
            id = "math_geometry",
            title = "Circle Area / مساحة الدائرة",
            subject = "Geometry / هندسة",
            language = "English",
            questionPrompt = "If the radius of a circle is 7 cm, what is its approximate area? (use π = 22/7)\nA) 154 cm²\nB) 44 cm²\nC) 98 cm²\nD) 196 cm²"
        ),
        QuizPreset(
            id = "biology_cell",
            title = "Cell Biology / علم الأحياء",
            subject = "Biology / أحياء",
            language = "English",
            questionPrompt = "Which cellular organelle is responsible for generating most of the chemical energy needed by the cell (ATP)?\nA) Ribosome\nB) Golgi apparatus\nC) Mitochondria\nD) Lysosome"
        )
    )
}
