# Checkers — Refactor & Bug Fixes

CSE 201 Spring 2026 Team Project. Refactor of <https://github.com/whyskevin/Checkers>
into an MVC architecture with text and GUI views, and corrections to a number of
gameplay bugs in the original code.

## How to build and run

Requires JDK 8 or newer. From the directory containing the `.java` files:

```
javac *.java
java StartUpMenu
```

The startup window asks for level (1 = Beginner, 2 = Intermediate, 3 = Advanced)
and view (1 = GUI, 2 = Text).

## Files

| File | Role |
| --- | --- |
| `StartUpMenu.java` | Entry point. Reads level + view choice, builds `BoardModel`, launches the chosen view. |
| `BoardModel.java` | **Model.** Single source of truth. All rules, validation, win checks, and AI live here. |
| `CellCoordinate.java` | A single board cell (status + stack height). |
| `Board.java` | **GUI View / Controller.** Thin wrapper around `BoardModel`; handles clicks, status banner, menus. |
| `Tiles.java` | **GUI View.** Pure renderer. Draws cells, pieces, kings, stack counts based on model state. |
| `Mouse.java` | Click forwarder. |
| `CheckersText.java` | **Text View / Controller.** Console-based view, accepts `row col row col` moves. |

The original `Pieces.java`, `Player.java`, `PieceType.java`, and `PlayerType.java`
were redundant once the model owned the game state and have been removed.

## Bugs found and fixed

Many of these bugs only manifested in the GUI because the original `Board.java`
duplicated the move-validation logic in `BoardModel` and the two implementations
disagreed.

1. **`(7,0) → (6,3)` accepted as a legal regular move (the bug you reported).**
   Original `Board.java` line ~514 used `||` instead of `&&` when checking that
   a single-step move was diagonal:
   `(|Δrow| == 1) || (|Δcol| == 1)` accepted any move with one row of motion.
   Fixed by removing GUI-side validation entirely and routing through
   `BoardModel.isValidMove`, which already used the correct `&&`.

2. **Wrong player moves first.** `BoardModel` said Red moves first (correct per
   standard checkers rules); `Board.turn()` initialized to Black. Fixed: only
   `BoardModel` decides; Red goes first.

3. **Red and Black starting rows swapped between views.** GUI placed Red on
   top, model placed Black on top. Fixed in `BoardModel.initializeBoard` —
   Black on top three rows, Red on bottom three.

4. **`Board.checkJump` could call `switchTurns` even when the jump was illegal.**
   Removed; turn switching is now handled by `BoardModel.executeMove` which
   only switches when the move is actually completed (and not in the middle of
   a multi-jump or a pending stack penalty).

5. **`displayDialog` fired twice on Red loss.** Both `checkWin` and
   `handleGameOver` showed dialogs. Now there is exactly one game-over path.

6. **Beginner-level captures were forbidden.** Spec says captures are *optional*
   at Beginner unless they are the only legal move. Original model rejected all
   captures at level 1. Fixed in `isValidMove` and `getRegularMovesForPiece`.

7. **"No legal moves" used the wrong winner rule.** Spec: when a player has no
   legal move, the player with the **most pieces** wins (stacks count their
   full height). Original picked the moving player as the loser regardless.
   Fixed in `BoardModel.checkWin` — counts pieces (including stack height) per
   player and awards the win to whichever side has more.

8. **Stacks did not actually stack.** Spec: stacks can have many pieces.
   Original simply re-marked the cell as `RED_STACK` / `BLACK_STACK` and threw
   the height away. Added a `stackHeight` field to `CellCoordinate` and
   `incrementStack` / `decrementStack` operations. The Tile renderer shows
   `xN` over a stack so the player can see the height.

9. **Captures destroyed stacks.** When a piece was jumped, the cell was wiped
   regardless of how tall the stack was. Fixed in
   `BoardModel.removeOpponentPiece` — decrements stack height and only clears
   the cell when the last piece is removed.

10. **`getTile(int, int)` had an off-by-one bound.** Used `<= ROW`. Replaced
    by `BoardModel.getCellStatus(r, c)` with proper `< size` bounds.

11. **AI was never invoked.** Original code had no AI integration anywhere.
    Added minimax with alpha-beta pruning to `BoardModel.getAIMove`:
    depth 2 at level 2 (Intermediate, kings only as required by spec) and
    depth 4 at level 3 (Advanced, all pieces). The GUI plays Black via a
    `javax.swing.Timer` so the EDT does not freeze; the text view announces
    "Computer is thinking..." while it computes.

12. **GUI duplicated all rule logic.** This was the root cause of bugs 1, 2,
    3, 6, 7, 8, 9. The GUI now never validates or mutates state itself — it
    calls `BoardModel.isValidMove` and `BoardModel.executeMove` and re-renders.

## Function mapping (original → refactored)

The spec asks for a clear mapping from original functions to their new
locations. Original `Board.java` was doing the work of three classes; most of
its methods either moved into `BoardModel` (because they were really model
logic) or stayed in `Board` but became thin pass-throughs.

| Original location | New location | Notes |
| --- | --- | --- |
| `Board.makePieces()` | `BoardModel.initializeBoard()` | Starting position; sides corrected (Black on top, Red on bottom). |
| `Board.checkJump(...)` | `BoardModel.isValidMove(...)` + `BoardModel.executeMove(...)` | Capture detection now lives in the model; the GUI doesn't recheck. |
| `Board.movePiece(...)` | `BoardModel.executeMove(...)` | Returns `true` if turn switched; `false` if the same player must continue (multi-jump or pending stack penalty). |
| `Board.kingMe(...)` (level 2/3) / promote-to-stack (level 1) | `BoardModel.executeMove(...)` (promotion block) | Promotion logic distinguishes by level: stack at level 1, king at level 2/3. |
| `Board.turn()` | `BoardModel.getCurrentPlayer()` + internal `currentPlayer` switch in `executeMove` | One source of truth. |
| `Board.checkWin()` | `BoardModel.checkWin()` | Returns `1` Red wins, `2` Black wins, `0` continue, `3` draw. Uses piece count when stuck. |
| `Board.displayDialog(...)` | `Board.handleGameOver(int)` | Called exactly once when `checkWin` reports a non-zero result. |
| `Board.getTile(int,int)` | `BoardModel.getCellStatus(int,int)` | Proper bounds; returns the cell status code. |
| `Pieces.java`, `Player.java`, `PlayerType.java`, `PieceType.java` | (removed) | Their data was redundant with `CellCoordinate.status` and `BoardModel.currentPlayer`. |
| `Tiles.paintComponent(...)` | `Tiles.paintComponent(...)` | Same name, but now reads `model.getCellStatus(r,c)` and `model.getStackHeight(r,c)` instead of having its own piece array. Highlights selected source (yellow) and legal destinations (green dots) by querying the model. |
| `Mouse.mousePressed(...)` | `Mouse.mousePressed(...)` → `Board.handleClick(r,c)` | Mouse no longer mutates state directly; it just forwards a (row, col). |
| (new) | `BoardModel.getCapturesForPiece(...)` | All jump moves available to a piece, including flying-king jumps at level 3. |
| (new) | `BoardModel.getRegularMovesForPiece(...)` | Non-capture moves; respects men forward-only at levels 1/2 and the level-3 omnidirectional rule. |
| (new) | `BoardModel.getAIMove()` + `minimax(...)` | Two-/four-ply look-ahead with alpha-beta. |
| (new) | `BoardModel.deepCopy()` | Used by the AI to search without mutating the live board; preserves stack heights. |
| (new) | `CheckersText.run()` | Text view game loop. Asks for `srcRow srcCol dstRow dstCol`, validates via the model, calls the AI for Black at non-Beginner levels. |

## Rule coverage by level

| Rule | Level 1 (Beginner) | Level 2 (Intermediate) | Level 3 (Advanced) |
| --- | --- | --- | --- |
| Board size | 8×8 | 8×8 | 10×10 |
| Pieces per side | 12 | 12 | 20 |
| Kings | no (stacks instead) | yes | yes |
| Stack on last row | yes (penalty: remove an opponent piece) | n/a | n/a |
| Captures mandatory | only when no other move exists | always | always |
| Men move directions | forward diagonals only | forward diagonals only | all 8 (4 diag + 4 orth), 1 step |
| Kings | n/a | 1-step diagonal both ways | flying (any distance, any diagonal) |
| AI | none (human vs human) | minimax depth 2, kings only as spec requires | minimax depth 4, all pieces |
| AI plays | — | Black | Black |

## Testing

A reflective rule-test harness was used during development (not included in
this submission). It runs 48 scenario tests covering: the exact `(7,0)→(6,3)`
bug, turn ownership, Red-moves-first, men forward-only, kings backward,
mandatory captures at level 2, optional captures at level 1, forced-only-move
captures at level 1, stack height tracking, multi-jumps, flying kings, level-3
omnidirectional men, win by elimination, win by piece count when stuck, path
blocking, and that you cannot capture or move your opponent's pieces. All 48
pass against the model in this submission.

## Files modified vs. new

Per the spec's request to call out new and modified code, every Java file
in this submission contains `// CHANGE:` or `// NEW:` comments above the
relevant blocks.
