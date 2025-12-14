/*

Java automatically converts operations with `char` to `int`, allowing

puzzleCharCounts changed from Map<Character, Integer> to int[]
availableHints changed from Set<Character> to List<Character>


revealHint()
O(1) operation: Hints were randomized once in initializeHints,
so we just remove the last element for quick access and removal.
This is superior to O(N) list copying/Set iteration.

evaluateGuess()
Logic Change 1b: Use int[] for O(1) character counting
O(1) clone of a small array (26 elements) is faster and cleaner than HashMap clone.

*/

import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Wordle {
    private static final String WORD_FILE = "src\\all_words.txt";
    private static final int DEFAULT_WORD_LENGTH = 5;
    private static final int DEFAULT_MAX_ATTEMPTS = 6;
    private static final Random RANDOM = new Random();

    // ASCII / ANSI escape sequences used to customize the output in the terminal
    private static final String RESET_ANSI = "\u001B[0m";
    private static final String BOLD_TEXT = "\u001B[1m";
    private static final String GREEN_BACKGROUND = "\u001B[42m";
    private static final String YELLOW_BACKGROUND = "\u001B[43m";
    private static final String GRAY_BACKGROUND = "\u001B[100m";
    private static final String BLACK_BACKGROUND = "\u001B[40m";
    private static final String RED_TEXT = "\u001B[31m";
    private static final String PURPLE_TEXT = "\u001B[1;95m";
    private static final String BLACK_TEXT = "\u001b[1;90m";

    private static final int STATE_DEFAULT = 0; // displayed letter state: default, aka not yet used by the player
    private static final int STATE_MISS = 1; // displayed letter state: invalid letter in the current puzzle
    private static final int STATE_WRONG_POS = 2; // displayed letter state: valid letter in the wrong index
    private static final int STATE_CORRECT = 3; // displayed letter state: valid letter in the right index
    private static final Set<Character> VOWELS = Set.of( 'A', 'E', 'I', 'O', 'U' ); // immutable structure used in the logic of initializing hints
    private static final String[] QWERTY_ROWS = { "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM" }; // immutable structure to order the layout of the displayed letters

    private final int wordLength;
    private final int maxAttempts;
    private final Set<String> dictionarySet; // immutable structure used in the logic of evaluating the player's guesses (needed constant look-up time) 
    private final List<String> dictionaryList; // immutable structure used to retrieve a random puzzle/word before the game starts
    private Set<String> alreadyGuessed; // structure used to keep track of words the player enters each round (prevents duplicate guesses; also needs constant look-up time)
    private int[] alphabetStates; // structure used to keep track of the `displayed letter state` for each letter in the alphabet (i.e., green, yellow, gray, none)
    private int[] puzzleCharCounts; // structure used to keep track of the number of each letter used in the puzzle (all 26 letters) (necessary for words with duplicate letters)
    private final String chosenPuzzle; // immutable access of the puzzle/word
    private int remainingHints;
    private Set<Character> revealedHints; // initialized to a TreeSet for storing an ordered collection of non-duplicate Hints (printed every round/before a guess)
    private List<Character> availableHints; // initialized to an ArrayList that was shuffled/randomized upon creation; stores hints used for the puzzle, allowing them to be accessed with .removeLast()

    // planned to have multiple constructor versions but they might've been worse for readability
    private Wordle(String wordLength, String maxAttempts, String chosenWord) {
        this.wordLength = parseOrDefault(wordLength, DEFAULT_WORD_LENGTH);
        this.maxAttempts = parseOrDefault(maxAttempts, DEFAULT_MAX_ATTEMPTS);

        dictionarySet = loadDictionary(this.wordLength);
        dictionaryList = new ArrayList<>(dictionarySet);

        if (isChosenWordValid(chosenWord)) {
            this.chosenPuzzle = chosenWord.toUpperCase();

            if (dictionarySet.add(this.chosenPuzzle)) {
                dictionaryList.addLast(this.chosenPuzzle); // remove for max performance? (ArrayList resizing) since it isn't used after this point
            }
        } else {
            this.chosenPuzzle = getRandomWord();
        }

        availableHints = initializeHints(this.chosenPuzzle);
        alreadyGuessed = new HashSet<>();
        puzzleCharCounts = new int[26]; //new HashMap<>()

        for (char letter : chosenPuzzle.toCharArray()) {
            puzzleCharCounts[letter - 'A']++; //puzzleCharCounts.compute(letter, (k, count) -> (count == null) ? 1 : count + 1)
        }

        alphabetStates = new int[26];

        Arrays.fill(this.alphabetStates, STATE_DEFAULT);
    }

    // converts the potential argument line value from String to int
    private int parseOrDefault(String arg, int defaultValue) {
        try {
            int value = Integer.parseInt(arg);

            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }

    // filters `Words.txt` to retrieve a HashSet of non-duplicate, upper-cased Strings of the desired length || originally used a List cause I wasnn't sure if it was better to convert Set->List or vice-versa
    private /*List*/Set<String> loadDictionary(int wordLength) {
        try (Stream<String> stream = Files.lines(Path.of(WORD_FILE), StandardCharsets.UTF_8)) {
            return stream
                //.map(String::trim)
                .map(String::toUpperCase)
                .filter(word -> word.length() == wordLength && word.chars().allMatch(Character::isLetter))
                //.toList()
                //.collect(Collectors.toSet())
                .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException _) {
            System.out.printf("%sNo %d-letter words in `%s`", RED_TEXT, wordLength, WORD_FILE);

            return Collections.emptySet(); //Collections.emptyList()
        }
    }

    private boolean isChosenWordValid(String word) {
        return word != null && word.length() == wordLength && word.chars().allMatch(Character::isLetter);
    }

    private String getRandomWord() {
        return dictionaryList.get(RANDOM.nextInt(dictionaryList.size()));
    }

    /*
        fills a List that is 40% of the puzzle/word length
        with vowels and/or consonants to be used as hints

        Sets used initially to filter potential duplicate letters
        then converted to Lists, allowing the Class shuffle/randomization method to be used

        the shuffle/randomization is done here to prevent it needing to be done every time (O(n) each call)
        a hint needs to be accessed ( e.g., availableHints[randomIndex] )
        this also prevents needing to convert from a Set to List or Array every time ^

        returns a List of the proper amount of both vowels and consonants that is shuffled a final time

        ^ after updating the remainingHints and revealedHints fields
    */
    private /*Set*/List<Character> initializeHints(String word) {
        int maxHints = (int) (word.length() * 0.40); // (word.length() * 40) / 100

        if (maxHints == 0) return new ArrayList<>(); // new HashSet<>()

        Set<Character> wordVowels = HashSet.newHashSet(wordLength);
        Set<Character> wordConsonants = HashSet.newHashSet(wordLength);

        for (char letter : word.toCharArray()) {
            if (VOWELS.contains(letter)) {
                wordVowels.add(letter);
            } else {
                wordConsonants.add(letter);
            }
        }

        List<Character> vowelList = new ArrayList<>(wordVowels);
        List<Character> consonantList = new ArrayList<>(wordConsonants);

        Collections.shuffle(vowelList, RANDOM);
        Collections.shuffle(consonantList, RANDOM);

        int vowelsNeeded = Math.min(maxHints / 2, vowelList.size());
        int consonantsNeeded = Math.min(maxHints - vowelsNeeded, consonantList.size());

        List<Character> finalSelection = new ArrayList<>(vowelsNeeded + consonantsNeeded);
        finalSelection.addAll(vowelList.subList(0, vowelsNeeded));
        finalSelection.addAll(consonantList.subList(0, consonantsNeeded));

        Collections.shuffle(finalSelection, RANDOM);

        this.remainingHints = finalSelection.size();
        this.revealedHints = new TreeSet<>();

        return finalSelection;
    }

    // logic for printing everything in the terminal before each guess
    private void printRoundLabels(int attempt) {
        String hintsString = revealedHints.isEmpty() ? "None" : revealedHints.toString();
        String borderString = "-----------------------------------------------------------------------------------------";

        System.out.printf(
            "%n%s%n%-30s%-30s%-30s%n%s%n%n",
            borderString,
            "Attempt " + attempt + " of " + maxAttempts,
            "Revealed Hints: " + hintsString,
            "Hints Left: " + remainingHints + " (Enter 'H')",
            borderString
        );

        List<String> displayRows = new ArrayList<>(QWERTY_ROWS.length);

        for (String rowLetters : QWERTY_ROWS) {
            StringBuilder rowString = new StringBuilder();

            for (char letter : rowLetters.toCharArray()) {
                String color = getLetterColors(alphabetStates[letter - 'A']);

                rowString.append(color).append(" ").append(letter).append(" ").append(RESET_ANSI).append(" ");
            }

            displayRows.add(rowString.toString());
        }

        int maxWidth = displayRows.stream()
            .mapToInt(this::getVisibleLength)
            .max()
            .orElse(0);

        for (String row : displayRows) {
            int width = getVisibleLength(row);
            int padding = (maxWidth - width) / 2;

            System.out.print(" ".repeat(Math.max(0, padding)));
            System.out.println(row);
        }

        System.out.println();
    }

    // could probably put them in an Array, idk if it would be faster
    // maps the state numerical value to the ASCI background color String
    // preventing the need to store each String in a collection
    private String getLetterColors(int state) {
        switch (state) {
            case STATE_CORRECT: return GREEN_BACKGROUND;
            case STATE_WRONG_POS: return YELLOW_BACKGROUND;
            case STATE_MISS: return GRAY_BACKGROUND;
            default: return BLACK_BACKGROUND;
        }
    }

    // removes the ASCI String from 
    private int getVisibleLength(String string) {
        return string.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }

    private String getValidGuess(Scanner scanner) {
        String guess;

        while (true) {
            String hintPrompt = remainingHints <= 0 ? "" : " (or 'H' for a hint)";

            System.out.printf("%sEnter a real %d-letter word%s%s: ", BOLD_TEXT, wordLength, RESET_ANSI, hintPrompt);

            guess = scanner.nextLine().toUpperCase();

            if (guess.equals("H")) {
                revealHint();
                
                continue;
            }

            if (guess.length() != wordLength) {
                System.out.printf("%sError: Word must be exactly %d letters.%s%n", RED_TEXT, wordLength, RESET_ANSI);
            } else if (!dictionarySet.contains(guess)) {
                System.out.printf("%sError: Not a valid word.%s%n", RED_TEXT, RESET_ANSI);
            } else if (!alreadyGuessed.add(guess)) {
                System.out.printf("%sError: You already guessed that word.%s%n", RED_TEXT, RESET_ANSI);
            } else {
                return guess;
            }
        }
    }

    private void revealHint() {
        if (remainingHints <= 0 || availableHints.isEmpty()) {
            System.out.printf("%sError: No hints remaining.%s%n", RED_TEXT, RESET_ANSI);

            return;
        }

        char hint = availableHints.removeLast();
        remainingHints--;

        revealedHints.add(hint);

        System.out.printf("%n%s Hint Revealed: The word contains the letter %s'%c' %s%n%n", BLACK_BACKGROUND, PURPLE_TEXT, hint, RESET_ANSI);
    }

    private String evaluateGuess(String guess) {
        int[] localCounts = puzzleCharCounts.clone();
        String[] colorResult = new String[wordLength];
        
        // Pass 1: Greens (Correct Position)
        for (int i = 0; i < wordLength; i++) {
            char c = guess.charAt(i);
            int index = c - 'A';

            if (c == chosenPuzzle.charAt(i)) {
                colorResult[i] = GREEN_BACKGROUND;
                localCounts[index]--; 
                
                updateAlphabetState(c, STATE_CORRECT);
            }
        }

        // Pass 2: Yellows (Wrong Position) & Grays (Misses)
        for (int i = 0; i < wordLength; i++) {
            if (colorResult[i] != null) continue;

            char c = guess.charAt(i);
            int index = c - 'A';

            // O(1) check if count > 0 using array index
            if (localCounts[index] > 0) {
                colorResult[i] = YELLOW_BACKGROUND;
                localCounts[index]--;
                
                updateAlphabetState(c, STATE_WRONG_POS);
            } else {
                colorResult[i] = GRAY_BACKGROUND;
                
                updateAlphabetState(c, STATE_MISS);
            }
        }

        StringBuilder stringResult = new StringBuilder();
        
        for (int i = 0; i < wordLength; i++) {
            stringResult.append(colorResult[i]).append(BLACK_TEXT).append(" ").append(guess.charAt(i)).append(" ").append(RESET_ANSI);
        }

        return stringResult.toString();
    }

    private void updateAlphabetState(char letter, int newState) {
        int index = letter - 'A';

        if (newState > alphabetStates[index]) {
            alphabetStates[index] = newState;
        }
    }

    private void play() {
        Scanner inputScanner = new Scanner(System.in);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            printRoundLabels(attempt);

            String guess = getValidGuess(inputScanner);
            String result = evaluateGuess(guess);
            
            System.out.printf("%n%s%n", result);

            if (guess.equals(chosenPuzzle)) {
                System.out.printf("%nCongratulations! You guessed the word in %d attempts.%n%n", attempt);
                inputScanner.close();

                return;
            }
        }

        String displayPuzzle = chosenPuzzle.replace("", " ").trim();

        System.out.printf("%nBetter luck next time...%nThe word was: %s%s%s %s %s%n%n", BLACK_BACKGROUND, RED_TEXT, BOLD_TEXT, displayPuzzle, RESET_ANSI);
        inputScanner.close();
    }

    public static void main(String[] args) {
        String chosenLength = args.length > 0 ? args[0] : "";
        String chosenAttempts = args.length > 1 ? args[1] : "";
        String desiredWord = args.length > 2 ? args[2] : "";

        try {
            Wordle game = new Wordle(chosenLength, chosenAttempts, desiredWord);

            game.play();
        } catch (Exception exception) {
            System.out.println("Game crashed: " + exception.getMessage());
        }
    }
}
