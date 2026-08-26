package com.example.util

import java.security.SecureRandom

enum class NameGenerationMode {
    FULL_NAME,
    FIRST_NAME_ONLY,
    LAST_NAME_ONLY,
    FIRST_AND_LAST_SEPARATE
}

data class GeneratedName(
    val firstName: String,
    val lastName: String,
    val fullName: String = "$firstName $lastName",
    val mode: NameGenerationMode = NameGenerationMode.FULL_NAME,
    val copiedText: String = fullName
)

object NameGenerator {

    private val random = SecureRandom()

    private val FIRST_NAMES = listOf(
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
        "David", "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Lisa", "Daniel", "Nancy",
        "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
        "Steven", "Kimberly", "Andrew", "Emily", "Paul", "Donna", "Joshua", "Michelle",
        "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah",
        "Timothy", "Stephanie", "Ronald", "Rebecca", "Jason", "Sharon", "Edward", "Laura",
        "Jeffrey", "Cynthia", "Ryan", "Dorothy", "Jacob", "Amy", "Gary", "Kathleen",
        "Nicholas", "Angela", "Eric", "Shirley", "Jonathan", "Emma", "Stephen", "Brenda",
        "Larry", "Pamela", "Justin", "Nicole", "Scott", "Anna", "Brandon", "Samantha",
        "Benjamin", "Katherine", "Samuel", "Christine", "Gregory", "Debra", "Alexander", "Rachel",
        "Frank", "Carolyn", "Patrick", "Janet", "Raymond", "Maria", "Jack", "Heather",
        "Dennis", "Diane", "Jerry", "Virginia", "Tyler", "Julie", "Aaron", "Joyce",
        "Jose", "Victoria", "Adam", "Olivia", "Nathan", "Kelly", "Henry", "Christina",
        "Zachary", "Lauren", "Douglas", "Joan", "Peter", "Evelyn", "Kyle", "Judith",
        "Noah", "Megan", "Ethan", "Cheryl", "Jeremy", "Andrea", "Christian", "Hannah",
        "Walter", "Martha", "Keith", "Jacqueline", "Austin", "Frances", "Roger", "Gloria",
        "Terry", "Ann", "Sean", "Teresa", "Gerald", "Kathryn", "Carl", "Sara",
        "Dylan", "Janice", "Harold", "Jean", "Jordan", "Alice", "Jesse", "Madison",
        "Bryan", "Doris", "Lawrence", "Abigail", "Arthur", "Julia", "Gabriel", "Judy"
    )

    private val LAST_NAMES = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
        "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
        "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
        "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker",
        "Cruz", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy",
        "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey",
        "Reed", "Kelly", "Howard", "Ramos", "Kim", "Cox", "Ward", "Richardson",
        "Watson", "Brooks", "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza",
        "Ruiz", "Hughes", "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers",
        "Long", "Ross", "Foster", "Jimenez", "Powell", "Jenkins", "Perry", "Russell"
    )

    fun generateName(mode: NameGenerationMode = NameGenerationMode.FULL_NAME): GeneratedName {
        val first = FIRST_NAMES[random.nextInt(FIRST_NAMES.size)]
        val last = LAST_NAMES[random.nextInt(LAST_NAMES.size)]
        val full = "$first $last"

        val textToCopy = when (mode) {
            NameGenerationMode.FULL_NAME -> full
            NameGenerationMode.FIRST_NAME_ONLY -> first
            NameGenerationMode.LAST_NAME_ONLY -> last
            NameGenerationMode.FIRST_AND_LAST_SEPARATE -> "$first\n$last"
        }

        return GeneratedName(
            firstName = first,
            lastName = last,
            fullName = full,
            mode = mode,
            copiedText = textToCopy
        )
    }
}
