package com.example.checkers;

// class that defines a simple move on the board
public class Move {
    private int startX, startY, endX, endY;

    public Move(int startX, int startY, int endX, int endY) {

        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public void perform(boolean isBlack, boolean isKing) {
        GameActivity.imageViewsTiles[this.startX][this.startY].setImageResource(android.R.color.transparent);
        GameActivity.imageViewsTiles[this.startX][this.startY].setClickable(false);
        if (isBlack)
            if (isKing) {
                GameActivity.imageViewsTiles[this.endX][this.endY].setImageResource(R.drawable.black_king);
                GameActivity.imageViewsTiles[this.endX][this.endY].setTag(R.drawable.black_king);
            } else {
                GameActivity.imageViewsTiles[this.endX][this.endY].setImageResource(R.drawable.black_piece);
                GameActivity.imageViewsTiles[this.endX][this.endY].setTag(R.drawable.black_piece);
            }

        else if (isKing) {
            GameActivity.imageViewsTiles[this.endX][this.endY].setImageResource(R.drawable.red_king);
            GameActivity.imageViewsTiles[this.endX][this.endY].setTag(R.drawable.red_king_highlighted);
        } else{
            GameActivity.imageViewsTiles[this.endX][this.endY].setImageResource(R.drawable.red_piece);
            GameActivity.imageViewsTiles[this.endX][this.endY].setTag(R.drawable.red_piece);
        }

    }


    public int getStartX() {
        return startX;
    }

    public void setStartX(int startX) {
        this.startX = startX;
    }

    public int getStartY() {
        return this.startY;
    }

    public void setStartY(int startY) {
        this.startY = startY;
    }

    public int getEndX() {
        return this.endX;
    }

    public void setEndX(int endX) {
        this.endX = endX;
    }

    public int getEndY() {
        return this.endY;
    }

    public void setEndY(int endY) {
        this.endY = endY;
    }

}
