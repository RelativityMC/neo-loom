package net.fabricmc.example;

import net.neoforged.fml.common.Mod;

@Mod("modid")
public class ExampleMod {
	public static void doSomething() {
		net.minecraft.server.world.ServerChunkManager.MainThreadExecutor.class.getClass();
		net.minecraft.util.Identifier identifier = net.minecraft.util.Identifier.ofValidated("a", "b");
		identifier.example$someDefaultMethod(); // interface injection
		((net.fabricmc.example.ExampleInterface) identifier).example$someMethod(); // removed final
		// int a = net.minecraft.util.math.BlockPos.BIT_SHIFT_X; // TODO not implemented
	}
}
