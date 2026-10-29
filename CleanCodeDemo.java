public class CleanCodeDemo {

    public static int calculateTotal(int price, int quantity) {
        if (price < 0 || quantity < 0) {
            throw new IllegalArgumentException(
                "Price and quantity must not be negative"
            );
        }

        return Math.multiplyExact(price, quantity);
    }

    public static boolean isEven(int number) {
        return number % 2 == 0;
    }

    public static void main(String[] args) {
        int total = calculateTotal(100, 3);
        System.out.println("Total: " + total);
        System.out.println("Is total even? " + isEven(total));
    }
}
