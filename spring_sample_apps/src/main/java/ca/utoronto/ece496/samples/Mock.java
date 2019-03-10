package ca.utoronto.ece496.samples;

/**
 * Created by Charlie on 09. 03 2019
 */
public class Mock {
    public static void sink(String data) {
        System.out.println(data);
    }

    public static String source() {
        return "tainted";
    }
}
