package com.github.jpingus;

public class StringFunctions {
    static boolean isEmpty(String... values) {
        for (String value : values)
            if (value == null || value.isEmpty()) return true;
        return false;
    }

}
