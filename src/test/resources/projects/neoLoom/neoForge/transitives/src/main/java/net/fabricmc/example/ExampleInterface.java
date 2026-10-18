package net.fabricmc.example;

public interface ExampleInterface {
	void example$someMethod();
	default void example$someDefaultMethod() {
	}
}
