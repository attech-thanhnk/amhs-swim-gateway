/*
 */
package vn.asg.converter.utils;

/**
 *
 * @author ThanhNk
 */
public class Builder {

    private StringBuilder builder = new StringBuilder();
    private String seperatedCharacter = " ";

    public Builder() {
    }

    public Builder(String seperateChar) {
        this.seperatedCharacter = seperateChar;
    }

    public void setSeperateCharacter(String value) {
        this.seperatedCharacter = value;
    }

    public Boolean isEmpty() {
        return this.builder.toString().isEmpty();
    }

    public Builder append(String str, Object... parameters) {
        if (!this.builder.toString().isEmpty()) {
            this.builder.append(this.seperatedCharacter);
        }

        if (parameters == null || parameters.length == 0) {
            this.builder.append(str);
        } else {
            this.builder.append(String.format(str, parameters));
        }

        return this;
    }

    @Override
    public String toString() {
        return this.builder.toString();
    }
}

