package io.github.lordraydenmk

import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.instances.io.monad.monad
import arrow.effects.liftIO
import arrow.syntax.collections.firstOption
import arrow.typeclasses.binding
import java.io.IOException
import kotlin.random.Random
import kotlin.streams.toList

fun putStrLn(line: String): IO<Unit> = IO { println(line) }

fun getStrLn(): IO<String> = IO { readLine() ?: throw IOException("Failed to read input!") }

enum class ExitStatus(val statusCode: Int) {
    SUCCESS(0),
    ERROR(1)
}

object Hangman {

    data class State(val name: String, val guesses: Set<Char> = emptySet(), val word: String) {

        val failures: Int = (guesses.toSet().minus(word.toSet())).size

        val playerLost: Boolean = failures > 8

        val playerWon: Boolean = (word.toSet().minus(guesses)).isEmpty()
    }

    val dictionary: List<String> by lazy {
        javaClass.classLoader.getResource("words.txt")
                .openStream()
                .bufferedReader()
                .lines()
                .toList()
    }

    fun run(): IO<ExitStatus> = hangman.attempt()
            .map {
                it.fold(
                        { ExitStatus.ERROR },
                        { ExitStatus.SUCCESS }
                )
            }

    val hangman: IO<Unit> = IO.monad().binding {
        putStrLn("Welcome to purely functional hangman").bind()
        val name = getName.bind()
        putStrLn("Welcome $name. Let's begin!").bind()
        val word = chooseWord.bind()
        val state = State(name, word = word)
        renderState(state).bind()
        gameLoop(state).bind()
        Unit
    }.fix()

    fun gameLoop(state: State): IO<State> = IO.monad().binding {
        val guess = getChoice().bind()
        val updatedState = state.copy(guesses = state.guesses.plus(guess))
        renderState(updatedState).bind()
        val loop = when {
            updatedState.playerWon -> putStrLn("Congratulations ${state.name} you won the game").map { false }
            updatedState.playerLost -> putStrLn("Sorry ${state.name} you lost the game. The word was ${state.word}").map { false }
            updatedState.word.contains(guess) -> putStrLn("You guessed correctly!").map { true }
            else -> putStrLn("That's wrong, but keep trying").map { true }
        }.bind()
        if (loop) gameLoop(updatedState).bind() else updatedState
    }.fix()

    val getName: IO<String> = IO.monad().binding {
        putStrLn("What is your name: ").bind()
        val name = getStrLn().bind()
        name
    }.fix()

    fun getChoice(): IO<Char> = IO.monad().binding {
        putStrLn("Please enter a letter").bind()
        val line = getStrLn().bind()
        val char = line.toLowerCase().trim().firstOption().fold(
                {
                    putStrLn("You did not enter a character")
                            .flatMap { getChoice() }
                },
                {
                    IO.just(it)
                }
        ).bind()
        char
    }.fix()

    fun nextInt(max: Int): IO<Int> = IO { Random.nextInt(max) }

    val chooseWord: IO<String> = IO.monad().binding {
        val rand = nextInt(dictionary.size).bind()
        dictionary[rand].liftIO().bind()
    }.fix()

    fun renderState(state: State): IO<Unit> {
        val word = state.word.toList().map { if (state.guesses.contains(it)) " $it " else "   " }
                .joinToString("")
        val line = state.word.map { " - " }.joinToString("")
        val guesses = "Guesses: ${state.guesses.toList().sorted().joinToString("")}"
        val text = "$word\n$line\n\n$guesses\n"
        return putStrLn(text)
    }
}

fun main() {
    Hangman.run().unsafeRunSync()
}