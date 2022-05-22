package com.example.checkers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import static com.example.checkers.DatabaseUtils.addDataToDatabase;
import static com.example.checkers.DatabaseUtils.isHost;
import static com.example.checkers.DatabaseUtils.getGuestUsername;

import java.util.HashMap;
import java.util.Map;

public class MyOnClickListenerForPieceMoves implements View.OnClickListener {

    public static final String TAG = "MyListenerForPieceMoves";
    private static ImageView[] lastUsedImageViews; // for removing the setOnClickListeners that we set and the player did not choose, so there will not be hanging listeners.
    private final Piece piece;
    private final Board board;
    private final String roomName;
    private final String playerName;
    private Context appContext; // for showing dialogs
    public final CollectionReference gameplayRef;
    public DocumentReference roomRef;
    public ListenerRegistration guestMovesUpdatesListener;
    public ListenerRegistration hostMovesUpdatesListener;


    public MyOnClickListenerForPieceMoves(Piece piece, Board board, String roomName, String playerName) {

        this.piece = piece;
        this.board = board;
        this.roomName = roomName;
        this.playerName = playerName;
        this.roomRef = FirebaseFirestore.getInstance().collection(WaitingRoomActivity.ROOMSPATH).document(roomName);
        this.gameplayRef = roomRef.collection("gameplay");
        this.hostMovesUpdatesListener = null;
        this.guestMovesUpdatesListener = null;
        this.appContext = null;
        lastUsedImageViews = new ImageView[10];
    }

    @Override
    public void onClick(View v) {
        displayMoveOptionsAndMove(this.piece.getX(), this.piece.getY(), this.piece.isBlack(), this.piece.isKing(), (ImageView) v);
    }

    public void displayMoveOptionsAndMove(int x, int y, boolean isBlack, boolean isKing, ImageView pieceImage) {

        this.appContext = pieceImage.getContext();
        clearPossibleLocationMarkers();
        unsetOnClickLastImageViews();

        if (isHost(playerName, roomName)) // for the host (for black)
        {
            if (isBlack && getIsBlackTurn()) {
                highlightPiece(true, isKing, pieceImage);
                if (!isKing) {
                    /* -------------------------- left diagonal -------------------------- */
                    if (Logic.canBlackMoveUp(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x - 1, y - 1) /* left tile */) {
                        ImageView leftPieceImage = GameActivity.imageViewsTiles[x - 1][y - 1];
                        lastUsedImageViews[0] = leftPieceImage;
                        Move leftMove = new Move(x, y, x - 1, y - 1);
                        leftDiagonal(leftMove, leftPieceImage, true, false, false, 0);
                    }

                    /* -------------------------- left-JUMP diagonal -------------------------- */
                    if (Logic.hasSpaceForLeftJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y - 2) && !Logic.isTileAvailable(board, x - 1, y - 1) && !board.getBoardArray()[x - 1][y - 1].isBlack()) {
                        ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y - 2];
                        lastUsedImageViews[1] = leftJumpPieceImage;
                        Move leftJumpMove = new Move(x, y, x - 2, y - 2);
                        leftDiagonal(leftJumpMove, leftJumpPieceImage, true, false, true, x - 1);
                    }

                    /* -------------------------- right diagonal -------------------------- */
                    if (Logic.canBlackMoveUp(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x - 1, y + 1) /* right tile */) {
                        Move rightMove = new Move(x, y, x - 1, y + 1);
                        ImageView rightPieceImage = GameActivity.imageViewsTiles[x - 1][y + 1];
                        lastUsedImageViews[2] = rightPieceImage;
                        rightDiagonal(rightMove, rightPieceImage, true, false, false, 0);
                    }

                    /* -------------------------- right-JUMP diagonal -------------------------- */
                    if (Logic.hasSpaceForRightJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y + 2) && !Logic.isTileAvailable(board, x - 1, y + 1) && !board.getBoardArray()[x - 1][y + 1].isBlack()) {
                        ImageView rightJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y + 2];
                        lastUsedImageViews[3] = rightJumpPieceImage;
                        Move rightJumpMove = new Move(x, y, x - 2, y + 2);
                        rightDiagonal(rightJumpMove, rightJumpPieceImage, true, false, true, x - 1);
                    }
                } else
                    kingMove(x, y, true);
            } else {
                // it is red's turn now.
                // set a listener for red's moves (guest moves) and move the red pieces accordingly
                DocumentReference guestMovesUpdatesRef = gameplayRef.document("guestMovesUpdates");
                guestMovesUpdatesListener = guestMovesUpdatesRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.w(TAG, "Listen failed.", error);
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {

                            String endAxis = (String) snapshot.get("endAxis"); // parsing the axis in the format: "X-Y"
                            String startAxis = (String) snapshot.get("startAxis"); // parsing the axis in the format: "X-Y"
                            Boolean isJump = (Boolean) snapshot.get("isJump");
                            Boolean isKingDb = (Boolean) snapshot.get("isKing");
                            Boolean isGameEnd = (Boolean) snapshot.get("isGameOver");
                            if (endAxis != null && startAxis != null && isKingDb != null) {
                                int startX = Integer.parseInt(startAxis.split("-")[0]);
                                int startY = Integer.parseInt(startAxis.split("-")[1]);
                                int endX = Integer.parseInt(endAxis.split("-")[0]);
                                int endY = Integer.parseInt(endAxis.split("-")[1]);
                                Move move = new Move(startX, startY, endX, endY);
                                move.perform(false, isKingDb);

                                // updating boardArray
                                board.getBoardArray()[endX][endY] = new Piece(endX, endY, false, isKingDb);
                                board.getBoardArray()[startX][startY] = null; // remove old piece

                                if (isJump != null) {
                                    if (isJump) { // if true: there was a jump, remove the jumped piece
                                        String jumpedAxis = (String) snapshot.get("jumpedAxis"); // // parsing the axis in the format: "X-Y"
                                        if (jumpedAxis != null) {
                                            int jumpedX = Integer.parseInt(jumpedAxis.split("-")[0]);
                                            int jumpedY = Integer.parseInt(jumpedAxis.split("-")[1]);

                                            GameActivity.imageViewsTiles[jumpedX][jumpedY].setImageResource(android.R.color.transparent);
                                            GameActivity.imageViewsTiles[jumpedX][jumpedY].setClickable(false);
                                            board.getBoardArray()[jumpedX][jumpedY] = null;
                                        } else
                                            Log.d(TAG, "Couldn't get jumpedAxis");
                                    }
                                }

                                if (isGameEnd != null) { // the players only update when they won, so when isGameEnd is not null, it means it must be true (so someone won)
                                    gameOver(false); // red won the game
                                }
                            }

                        }
                    }
                });

            }

        } else // for the guest (for red)
        {
            if (!isBlack && !getIsBlackTurn()) {
                highlightPiece(false, isKing, pieceImage);
                if (!isKing) {
                    /* -------------------------- left diagonal -------------------------- */
                    if (Logic.canRedMoveDown(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x + 1, y - 1) /* left tile */) {
                        Move leftMove = new Move(x, y, x + 1, y - 1);
                        ImageView leftPieceImage = GameActivity.imageViewsTiles[x + 1][y - 1];
                        lastUsedImageViews[4] = leftPieceImage;
                        leftDiagonal(leftMove, leftPieceImage, false, false, false, 0);
                    }

                    /* -------------------------- left-JUMP diagonal -------------------------- */

                    if (Logic.hasSpaceForLeftJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y - 2) && !Logic.isTileAvailable(board, x + 1, y - 1) && board.getBoardArray()[x + 1][y - 1].isBlack()) {
                        ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y - 2];
                        lastUsedImageViews[5] = leftJumpPieceImage;
                        Move leftJumpMove = new Move(x, y, x + 2, y - 2);
                        leftDiagonal(leftJumpMove, leftJumpPieceImage, false, false, true, x + 1);
                    }

                    /* -------------------------- right diagonal -------------------------- */
                    if (Logic.canRedMoveDown(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x + 1, y + 1) /* right tile */) {
                        Move rightMove = new Move(x, y, x + 1, y + 1);
                        ImageView rightPieceImage = GameActivity.imageViewsTiles[x + 1][y + 1];
                        lastUsedImageViews[6] = rightPieceImage;
                        rightDiagonal(rightMove, rightPieceImage, false, false, false, 0);
                    }

                    /* -------------------------- right-JUMP diagonal -------------------------- */
                    if (Logic.hasSpaceForRightJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y + 2) && !Logic.isTileAvailable(board, x + 1, y + 1) && board.getBoardArray()[x + 1][y + 1].isBlack()) {
                        ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y + 2];
                        lastUsedImageViews[7] = leftJumpPieceImage;
                        Move leftJumpMove = new Move(x, y, x + 2, y + 2);
                        rightDiagonal(leftJumpMove, leftJumpPieceImage, false, false, true, x + 1);
                    }

                } else
                    kingMove(x, y, false);
            } else {
                // it is black's turn now.
                // set a listener for black's moves (host pieces) and move the black pieces accordingly
                DocumentReference hostMovesUpdatesRef = gameplayRef.document("hostMovesUpdates");
                hostMovesUpdatesListener = hostMovesUpdatesRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.w(TAG, "Listen failed.", error);
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            String endAxis = (String) snapshot.get("endAxis"); // parsing the axis by the format: "X-Y"
                            String startAxis = (String) snapshot.get("startAxis"); // parsing the axis by the format: "X-Y"
                            Boolean isJump = (Boolean) snapshot.get("isJump");
                            Boolean isKingDb = (Boolean) snapshot.get("isKing");
                            Boolean isGameEnd = (Boolean) snapshot.get("isGameOver");
                            if (endAxis != null && startAxis != null && isKingDb != null) {
                                int startX = Integer.parseInt(startAxis.split("-")[0]);
                                int startY = Integer.parseInt(startAxis.split("-")[1]);
                                int endX = Integer.parseInt(endAxis.split("-")[0]);
                                int endY = Integer.parseInt(endAxis.split("-")[1]);
                                Move move = new Move(startX, startY, endX, endY);
                                move.perform(true, isKingDb);

                                // updating boardArray
                                board.getBoardArray()[endX][endY] = new Piece(endX, endY, true, isKingDb); // ***** change isKing here
                                board.getBoardArray()[startX][startY] = null; // remove old piece

                                if (isJump != null) {
                                    if (isJump) {
                                        String jumpedAxis = (String) snapshot.get("jumpedAxis"); // // parsing the axis in the format: "X-Y"
                                        if (jumpedAxis != null) {
                                            int jumpedX = Integer.parseInt(jumpedAxis.split("-")[0]);
                                            int jumpedY = Integer.parseInt(jumpedAxis.split("-")[1]);

                                            GameActivity.imageViewsTiles[jumpedX][jumpedY].setImageResource(android.R.color.transparent);
                                            GameActivity.imageViewsTiles[jumpedX][jumpedY].setClickable(false);
                                            board.getBoardArray()[jumpedX][jumpedY] = null;
                                        } else
                                            Log.d(TAG, "Couldn't get jumpedAxis");
                                    }
                                }

                                if (isGameEnd != null) // the players only update when they won, so when isGameEnd is not null, it means it must be true (so someone won)
                                    gameOver(true);
                            }
                        }
                    }
                });

            }
        }

//        ****play locally****
//        if (isBlack) {
//            highlightPiece(true, isKing, pieceImage);
//            if (!isKing) {
//                /* -------------------------- left diagonal -------------------------- */
//                if (Logic.canBlackMoveUp(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x - 1, y - 1) /* left tile */) {
//                    ImageView leftPieceImage = GameActivity.imageViewsTiles[x - 1][y - 1];
//                    lastUsedImageViews[0] = leftPieceImage;
//                    Move leftMove = new Move(x, y, x - 1, y - 1);
//                    leftDiagonal(leftMove, leftPieceImage, true, false, false, 0);
//                }
//
//                /* -------------------------- left-JUMP diagonal -------------------------- */
//                if (Logic.hasSpaceForLeftJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y - 2) && !Logic.isTileAvailable(board, x - 1, y - 1) && !board.getBoardArray()[x - 1][y - 1].isBlack()) {
//                    ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y - 2];
//                    lastUsedImageViews[1] = leftJumpPieceImage;
//                    Move leftJumpMove = new Move(x, y, x - 2, y - 2);
//                    leftDiagonal(leftJumpMove, leftJumpPieceImage, true, false, true, x - 1);
//                }
//
//                /* -------------------------- right diagonal -------------------------- */
//                if (Logic.canBlackMoveUp(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x - 1, y + 1) /* right tile */) {
//                    Move rightMove = new Move(x, y, x - 1, y + 1);
//                    ImageView rightPieceImage = GameActivity.imageViewsTiles[x - 1][y + 1];
//                    lastUsedImageViews[2] = rightPieceImage;
//                    rightDiagonal(rightMove, rightPieceImage, true, false, false, 0);
//                }
//
//                /* -------------------------- right-JUMP diagonal -------------------------- */
//                if (Logic.hasSpaceForRightJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y + 2) && !Logic.isTileAvailable(board, x - 1, y + 1) && !board.getBoardArray()[x - 1][y + 1].isBlack()) {
//                    ImageView rightJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y + 2];
//                    lastUsedImageViews[3] = rightJumpPieceImage;
//                    Move rightJumpMove = new Move(x, y, x - 2, y + 2);
//                    rightDiagonal(rightJumpMove, rightJumpPieceImage, true, false, true, x - 1);
//                }
//            } else
//                kingMove(x, y, true);
//        }
//        else if (!isBlack) {
//            highlightPiece(false, isKing, pieceImage);
//            if (!isKing) {
//                /* -------------------------- left diagonal -------------------------- */
//                if (Logic.canRedMoveDown(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x + 1, y - 1) /* left tile */) {
//                    Move leftMove = new Move(x, y, x + 1, y - 1);
//                    ImageView leftPieceImage = GameActivity.imageViewsTiles[x + 1][y - 1];
//                    lastUsedImageViews[4] = leftPieceImage;
//                    leftDiagonal(leftMove, leftPieceImage, false, false, false, 0);
//                }
//
//                /* -------------------------- left-JUMP diagonal -------------------------- */
//
//                if (Logic.hasSpaceForLeftJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y - 2) && !Logic.isTileAvailable(board, x + 1, y - 1) && board.getBoardArray()[x + 1][y - 1].isBlack()) {
//                    ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y - 2];
//                    lastUsedImageViews[5] = leftJumpPieceImage;
//                    Move leftJumpMove = new Move(x, y, x + 2, y - 2);
//                    leftDiagonal(leftJumpMove, leftJumpPieceImage, false, false, true, x + 1);
//                }
//
//                /* -------------------------- right diagonal -------------------------- */
//                if (Logic.canRedMoveDown(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x + 1, y + 1) /* right tile */) {
//                    Move rightMove = new Move(x, y, x + 1, y + 1);
//                    ImageView rightPieceImage = GameActivity.imageViewsTiles[x + 1][y + 1];
//                    lastUsedImageViews[6] = rightPieceImage;
//                    rightDiagonal(rightMove, rightPieceImage, false, false, false, 0);
//                }
//
//                /* -------------------------- right-JUMP diagonal -------------------------- */
//                if (Logic.hasSpaceForRightJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y + 2) && !Logic.isTileAvailable(board, x + 1, y + 1) && board.getBoardArray()[x + 1][y + 1].isBlack()) {
//                    ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y + 2];
//                    lastUsedImageViews[7] = leftJumpPieceImage;
//                    Move leftJumpMove = new Move(x, y, x + 2, y + 2);
//                    rightDiagonal(leftJumpMove, leftJumpPieceImage, false, false, true, x + 1);
//                }
//
//            } else
//                kingMove(x, y, false);
//        }
    }

    private void kingMove(int x, int y, boolean isBlack) {
        /* -------------------------- left diagonal BLACK -------------------------- */
        if (Logic.canBlackMoveUp(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x - 1, y - 1) /* left tile */) {
            ImageView leftPieceImage = GameActivity.imageViewsTiles[x - 1][y - 1];
            lastUsedImageViews[0] = leftPieceImage;
            Move leftMove = new Move(x, y, x - 1, y - 1);
            leftDiagonal(leftMove, leftPieceImage, isBlack, true, false, 0);
        }

        /* -------------------------- right diagonal BLACK -------------------------- */
        if (Logic.canBlackMoveUp(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x - 1, y + 1) /* right tile */) {
            Move rightMove = new Move(x, y, x - 1, y + 1);
            ImageView rightPieceImage = GameActivity.imageViewsTiles[x - 1][y + 1];
            lastUsedImageViews[1] = rightPieceImage;
            rightDiagonal(rightMove, rightPieceImage, isBlack, true, false, 0);
        }



        /* -------------------------- left diagonal RED -------------------------- */
        if (Logic.canRedMoveDown(x) && !Logic.isOnLeftEdge(y) && Logic.isTileAvailable(board, x + 1, y - 1) /* left tile */) {
            Move leftMove = new Move(x, y, x + 1, y - 1);
            ImageView leftPieceImage = GameActivity.imageViewsTiles[x + 1][y - 1];
            lastUsedImageViews[2] = leftPieceImage;
            leftDiagonal(leftMove, leftPieceImage, isBlack, true, false, 0);
        }

        /* -------------------------- right diagonal RED -------------------------- */
        if (Logic.canRedMoveDown(x) && !Logic.isOnRightEdge(y) && Logic.isTileAvailable(board, x + 1, y + 1) /* right tile */) {
            Move rightMove = new Move(x, y, x + 1, y + 1);
            ImageView rightPieceImage = GameActivity.imageViewsTiles[x + 1][y + 1];
            lastUsedImageViews[3] = rightPieceImage;
            rightDiagonal(rightMove, rightPieceImage, isBlack, true, false, 0);
        }


        if (isBlack) {
            /* -------------------------- left-JUMP diagonal BLACK -------------------------- */
            if (Logic.hasSpaceForLeftJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y - 2) && !Logic.isTileAvailable(board, x - 1, y - 1) && isCheckerBehindNeeds2BeRedOrBlack(true, x - 1, y - 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y - 2];
                lastUsedImageViews[4] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x - 2, y - 2);
                leftDiagonal(leftJumpMove, leftJumpPieceImage, true, true, true, x - 1);
            }
            /* -------------------------- right-JUMP diagonal BLACK -------------------------- */
            if (Logic.hasSpaceForRightJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y + 2) && !Logic.isTileAvailable(board, x - 1, y + 1) && isCheckerBehindNeeds2BeRedOrBlack(true, x - 1, y + 1)) {
                ImageView rightJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y + 2];
                lastUsedImageViews[5] = rightJumpPieceImage;
                Move rightJumpMove = new Move(x, y, x - 2, y + 2);
                rightDiagonal(rightJumpMove, rightJumpPieceImage, true, true, true, x - 1);
            }
            /* -------------------------- left-JUMP diagonal RED -------------------------- */
            if (Logic.hasSpaceForLeftJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y - 2) && !Logic.isTileAvailable(board, x + 1, y - 1) && isCheckerBehindNeeds2BeRedOrBlack(true, x + 1, y - 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y - 2];
                lastUsedImageViews[6] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x + 2, y - 2);
                leftDiagonal(leftJumpMove, leftJumpPieceImage, true, true, true, x + 1);
            }
            /* -------------------------- right-JUMP diagonal RED -------------------------- */
            if (Logic.hasSpaceForRightJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y + 2) && !Logic.isTileAvailable(board, x + 1, y + 1) && isCheckerBehindNeeds2BeRedOrBlack(true, x + 1, y + 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y + 2];
                lastUsedImageViews[7] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x + 2, y + 2);
                rightDiagonal(leftJumpMove, leftJumpPieceImage, true, true, true, x + 1);
            }
        } else {
            /* -------------------------- left-JUMP diagonal BLACK -------------------------- */
            if (Logic.hasSpaceForLeftJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y - 2) && !Logic.isTileAvailable(board, x - 1, y - 1) && isCheckerBehindNeeds2BeRedOrBlack(false, x - 1, y - 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y - 2];
                lastUsedImageViews[4] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x - 2, y - 2);
                leftDiagonal(leftJumpMove, leftJumpPieceImage, false, true, true, x - 1);
            }
            /* -------------------------- right-JUMP diagonal BLACK -------------------------- */
            if (Logic.hasSpaceForRightJump(x, y, true) && Logic.isTileAvailable(board, x - 2, y + 2) && !Logic.isTileAvailable(board, x - 1, y + 1) && isCheckerBehindNeeds2BeRedOrBlack(false, x - 1, y + 1)) {
                ImageView rightJumpPieceImage = GameActivity.imageViewsTiles[x - 2][y + 2];
                lastUsedImageViews[5] = rightJumpPieceImage;
                Move rightJumpMove = new Move(x, y, x - 2, y + 2);
                rightDiagonal(rightJumpMove, rightJumpPieceImage, false, true, true, x - 1);
            }
            /* -------------------------- left-JUMP diagonal RED -------------------------- */
            if (Logic.hasSpaceForLeftJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y - 2) && !Logic.isTileAvailable(board, x + 1, y - 1) && isCheckerBehindNeeds2BeRedOrBlack(false, x + 1, y - 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y - 2];
                lastUsedImageViews[6] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x + 2, y - 2);
                leftDiagonal(leftJumpMove, leftJumpPieceImage, false, true, true, x + 1);
            }
            /* -------------------------- right-JUMP diagonal RED -------------------------- */
            if (Logic.hasSpaceForRightJump(x, y, false) && Logic.isTileAvailable(board, x + 2, y + 2) && !Logic.isTileAvailable(board, x + 1, y + 1) && isCheckerBehindNeeds2BeRedOrBlack(false, x + 1, y + 1)) {
                ImageView leftJumpPieceImage = GameActivity.imageViewsTiles[x + 2][y + 2];
                lastUsedImageViews[7] = leftJumpPieceImage;
                Move leftJumpMove = new Move(x, y, x + 2, y + 2);
                rightDiagonal(leftJumpMove, leftJumpPieceImage, false, true, true, x + 1);
            }
        }
    }

    // only in the eating-checks we do, we need to check differently for black or red
    private boolean isCheckerBehindNeeds2BeRedOrBlack(boolean isBlack, int x, int y) {
        if (isBlack)
            return !board.getBoardArray()[x][y].isBlack(); // check if there is red piece behind me
        return board.getBoardArray()[x][y].isBlack(); // else, check if there is black piece behind me
    }


    private void rightDiagonal(Move rightMove, ImageView rightPieceImage, boolean isBlack, boolean isKing, boolean isJump, int jumpedPieceX) {
        rightPieceImage.setImageResource(R.drawable.possible_location_marker);
        rightPieceImage.setClickable(true);
        rightPieceImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int endX = rightMove.getEndX();
                int endY = rightMove.getEndY();
                int startX = rightMove.getStartX();
                int startY = rightMove.getStartY();

                // updating boardArray
                board.getBoardArray()[endX][endY] = new Piece(endX, endY, isBlack, isKing);
                board.getBoardArray()[startX][startY] = null; // remove old piece
                int jumpedPieceY = startY + 1;
                if (isJump) {
                    // delete the jumped piece
                    GameActivity.imageViewsTiles[jumpedPieceX][jumpedPieceY].setImageResource(android.R.color.transparent);
                    GameActivity.imageViewsTiles[jumpedPieceX][jumpedPieceY].setClickable(false);
                    board.getBoardArray()[jumpedPieceX][jumpedPieceY] = null;
                }
                clearPossibleLocationMarkers();
                unsetOnClickLastImageViews();

                // check if needs to be king
                if (Logic.isPieceNeeds2BeKing(isBlack, endX))
                    board.getBoardArray()[endX][endY].setKing();

                rightMove.perform(isBlack, board.getBoardArray()[endX][endY].isKing());

                isGameOver();

                // set onClick for the new piece (location)
                rightPieceImage.setClickable(true);
                rightPieceImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        displayMoveOptionsAndMove(endX, endY, isBlack, board.getBoardArray()[endX][endY].isKing(), rightPieceImage); // recursively show more move options
                    }
                });

                // updating next turn - passing the turn to the other player
                updateBlackTurnInDb(!isBlack);

                // upload new piece location to db
                uploadPieceLocationToDb(rightMove, isJump, jumpedPieceX, jumpedPieceY, board.getBoardArray()[endX][endY].isKing());
            }
        });
    }

    private void leftDiagonal(Move leftMove, ImageView leftPieceImage, boolean isBlack, boolean isKing, boolean isJump, int jumpedPieceX) {
        leftPieceImage.setImageResource(R.drawable.possible_location_marker);
        leftPieceImage.setClickable(true);
        leftPieceImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int endX = leftMove.getEndX();
                int endY = leftMove.getEndY();
                int startX = leftMove.getStartX();
                int startY = leftMove.getStartY();


                // updating boardArray
                board.getBoardArray()[endX][endY] = new Piece(/*leftPieceImage,*/ endX, endY, isBlack, isKing);
                board.getBoardArray()[startX][startY] = null; // remove old piece
                int jumpedPieceY = startY - 1;
                if (isJump) {
                    // delete the jumped piece
                    GameActivity.imageViewsTiles[jumpedPieceX][jumpedPieceY].setImageResource(android.R.color.transparent);
                    GameActivity.imageViewsTiles[jumpedPieceX][jumpedPieceY].setClickable(false);
                    board.getBoardArray()[jumpedPieceX][jumpedPieceY] = null;
                }
                clearPossibleLocationMarkers();
                unsetOnClickLastImageViews();

                // check if needs to be king
                if (Logic.isPieceNeeds2BeKing(isBlack, endX))
                    board.getBoardArray()[endX][endY].setKing();

                leftMove.perform(isBlack, board.getBoardArray()[endX][endY].isKing());

                isGameOver();

                // set onClick for the new piece (location)
                leftPieceImage.setClickable(true);
                leftPieceImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        displayMoveOptionsAndMove(endX, endY, isBlack, board.getBoardArray()[endX][endY].isKing(), leftPieceImage); // recursively show more move options
                    }
                });

                // updating next turn - passing the turn to the other player
                updateBlackTurnInDb(!isBlack);

                // upload new piece location to db
                uploadPieceLocationToDb(leftMove, isJump, jumpedPieceX, jumpedPieceY, board.getBoardArray()[endX][endY].isKing());
            }
        });
    }

    private void uploadPieceLocationToDb(Move move, boolean isJump, int jumpX, int jumpY, boolean isKing) {
        DocumentReference documentReference;
        if (isHost(playerName, roomName))
            documentReference = gameplayRef.document("hostMovesUpdates"); // for host updates
        else
            documentReference = gameplayRef.document("guestMovesUpdates"); // for guest updates
        Map<String, Object> updates = new HashMap<>();
        String startAxis = move.getStartX() + "-" + move.getStartY();
        String endAxis = move.getEndX() + "-" + move.getEndY();
        updates.put("startAxis", startAxis);
        updates.put("endAxis", endAxis);
        updates.put("isKing", isKing);
        updates.put("isJump", isJump);
        if (isJump)
            updates.put("jumpedAxis", jumpX + "-" + jumpY);
        addDataToDatabase(updates, documentReference);
    }

    public void isGameOver() {
        int redPieces = 0;
        int blackPieces = 0;
        for (int i = 0; i < Board.SIZE; i++)
            for (int j = 0; j < Board.SIZE; j++) {
                if (board.getBoardArray()[i][j] != null) {
                    if (board.getBoardArray()[i][j].isBlack())
                        blackPieces++;
                    else
                        redPieces++;
                }
            }
        // black won
        if (redPieces == 0){
            // update in db that black won (the host)
            DocumentReference hostMovesUpdatesRef = gameplayRef.document("hostMovesUpdates");
            Map<String, Object> updateGameOver = new HashMap<>();
            updateGameOver.put("isGameOver", true);
            addDataToDatabase(updateGameOver, hostMovesUpdatesRef);

            // show locally on black's phone that he won
            gameOver(true);
        }

        // red won
        else if (blackPieces == 0)
        {
            DocumentReference guestMovesUpdatesRef = gameplayRef.document("guestMovesUpdates");
            Map<String, Object> updateGameOver = new HashMap<>();
            updateGameOver.put("isGameOver", true);
            addDataToDatabase(updateGameOver, guestMovesUpdatesRef);

            // show locally on red's phone that he won
            gameOver(false);
        }

    }

    private void gameOver(boolean isBlack) {
        Log.d(TAG, "GAMEOVERRRRRRRRR*********");

        boolean host = isHost(roomName, playerName);

        AlertDialog.Builder gameRequestDialogBuilder = new AlertDialog.Builder(this.appContext, AlertDialog.THEME_HOLO_LIGHT);
        gameRequestDialogBuilder.setCancelable(false);
        gameRequestDialogBuilder.setTitle("Game is Over!");
        gameRequestDialogBuilder.setPositiveButton("Return Back To The Lobby", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                appContext.startActivity(new Intent(appContext, WaitingRoomActivity.class));
                ((Activity) appContext).finish(); // finish GameActivity
            }
        });

        if (isBlack)
        {
            // show popup that the host won (roomName = hostname)
            String hostUsername = roomName;
            gameRequestDialogBuilder.setMessage(hostUsername + " has won the game! he is probably better.");
        }
        else
        {
            // show popup that the guest won (getGuestUsername())
            String guestUsername;
            if (host) // on the host phone (he doesn't have the guest's username, so he has to get it from db
                guestUsername = getGuestUsername(roomRef);
            else // on the guest phone (the local username is stored in playerName)
                guestUsername = playerName;

            gameRequestDialogBuilder.setMessage(guestUsername + " has won the game! he is probably better.");
        }

        if (host)
        {
            // remove the guest from the room
            Map<String, Object> updates = new HashMap<>();
            updates.put("guest", FieldValue.delete()); // mark "guest" field as deletable on the database (remove it)
            updates.put("isInGame", false); // update isInGame to false
            addDataToDatabase(updates, roomRef);
        }

        AlertDialog gameRequestDialog;
        gameRequestDialog = gameRequestDialogBuilder.create();
        gameRequestDialog.show();

        // clean-up stuff
        if (hostMovesUpdatesListener != null)
            hostMovesUpdatesListener.remove();
        if (guestMovesUpdatesListener != null)
            guestMovesUpdatesListener.remove();

        // REMEMBER:
        // remove listeners, each hostMovesUpdates and guestMovesUpdates.
        // remove the guest from the room (like when the host declines or guest cancels in WaitingRoom)
    }

    private void highlightPiece(boolean isBlack, boolean isKing, ImageView piece) {
        if (isBlack) {
            if (isKing) {
                piece.setImageResource(R.drawable.black_king_highlighted);
                piece.setTag(R.drawable.black_king_highlighted);
            } else {
                piece.setImageResource(R.drawable.black_piece_highlighted);
                piece.setTag(R.drawable.black_piece_highlighted);
            }

        } else {
            if (isKing) {
                piece.setImageResource(R.drawable.red_king_highlighted);
                piece.setTag(R.drawable.red_king_highlighted);
            } else {
                piece.setImageResource(R.drawable.red_piece_highlighted);
                piece.setTag(R.drawable.red_piece_highlighted);
            }

        }
    }

    public void clearPossibleLocationMarkers() {
        for (int i = 0; i < Board.SIZE; i++) {
            for (int j = 0; j < Board.SIZE; j++) {
                if (Logic.isTileForChecker(i, j)) {
                    if (board.getBoardArray()[i][j] != null) {
                        Integer tag = (Integer) GameActivity.imageViewsTiles[i][j].getTag();
                        if (tag != null) {
                            if (board.getBoardArray()[i][j].isBlack()) {
                                if (tag == R.drawable.black_piece_highlighted) {
                                    GameActivity.imageViewsTiles[i][j].setImageResource(R.drawable.black_piece);
                                } else if (tag == R.drawable.black_king_highlighted) // king
                                {
                                    GameActivity.imageViewsTiles[i][j].setImageResource(R.drawable.black_king);
                                }

                            } else {
                                if (tag == R.drawable.red_piece_highlighted) {
                                    GameActivity.imageViewsTiles[i][j].setImageResource(R.drawable.red_piece);
                                } else if (tag == R.drawable.red_king_highlighted)// king
                                {
                                    GameActivity.imageViewsTiles[i][j].setImageResource(R.drawable.red_king);
                                }


                            }
                        }
                    } else // remove possible_loc_marker
                        GameActivity.imageViewsTiles[i][j].setImageResource(android.R.color.transparent);
                }
            }
        }
    }

    // responsible for removing the setOnClickListeners that we set and the player did not choose to go, so there will not be hanging listeners.
    public void unsetOnClickLastImageViews() {
        // how to make sure that at the place of the image there isn't also a checkers piece:
        // 1. get id of image; extract X and Y axis from it
        // 2. compare those X and Y in boardArray
        // 3. if a piece is found at that location: do not unset it!
        // 4. else: unset it.
        for (ImageView image : lastUsedImageViews) {
            if (image != null) {
                // get id of image and extract axis
                String idStr = image.getResources().getResourceEntryName(image.getId());
                String axis = idStr.substring(idStr.length() - 2);
                int x = Character.getNumericValue(axis.charAt(0));
                int y = Character.getNumericValue(axis.charAt(1));


                if (board.getBoardArray()[x][y] == null) {
                    image.setClickable(false);
                    image.setOnClickListener(null);
                }
            }
        }
    }

    private boolean getIsBlackTurn() {
        Task<DocumentSnapshot> getTurn = gameplayRef.document("gameUpdates").get();
        while (!getTurn.isComplete()) {
            System.out.println("waiting for getIsBlackTurn");
        }
        if (getTurn.isSuccessful()) {
            DocumentSnapshot isBlackTurnResult = getTurn.getResult();
            Boolean val = (Boolean) isBlackTurnResult.get("isBlackTurn");
            if (val != null)
                return (boolean) val;
        }
        Log.d(TAG, "Error getting document: ", getTurn.getException());
        throw new IllegalStateException("couldn't get isBlackTurn from db");
    }

    private void updateBlackTurnInDb(boolean blackTurn) {
        Map<String, Object> gameUpdates = new HashMap<>();
        gameUpdates.put("isBlackTurn", blackTurn);
        addDataToDatabase(gameUpdates, gameplayRef.document("gameUpdates"));
    }
}

