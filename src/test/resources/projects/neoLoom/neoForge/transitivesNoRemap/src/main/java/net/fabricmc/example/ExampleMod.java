package net.fabricmc.example;

import net.neoforged.fml.common.Mod;

@Mod("modid")
public class ExampleMod {
	public static void doSomething() {
		net.minecraft.server.level.ServerChunkCache.MainThreadExecutor.class.getClass();
		net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.createUntrusted("a", "b");
		identifier.example$someDefaultMethod(); // interface injection
		((net.fabricmc.example.ExampleInterface) identifier).example$someMethod(); // removed final
		int a = net.minecraft.core.BlockPos.X_OFFSET; // accessible
		net.minecraft.SharedConstants.CRASH_EAGERLY = true; // mutable
	}
}
