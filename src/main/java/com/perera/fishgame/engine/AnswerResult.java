package com.perera.fishgame.engine;

/** What happened when the player submitted an answer. */
public enum AnswerResult {
    /** Right answer: score went up. */
    CORRECT,
    /** Wrong answer: a life was lost, but the game continues. */
    WRONG,
    /** Wrong answer and it was the last life. */
    GAME_OVER
}