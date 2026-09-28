package net.krona.mutagen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.body.Bodies;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.mutation.Mutation;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Отладочные команды прототипа: посмотреть состояние, ввести дозу, выставить прогресс.
 * Нужны, потому что честный путь до пятой стадии занимает игровые дни.
 */
public final class MutagenCommand {
    private static final SuggestionProvider<CommandSourceStack> STRAIN_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(Strains.ids(), builder);

    private MutagenCommand() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mutagen")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("status")
                .executes(context -> status(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> status(context, EntityArgument.getPlayer(context, "target")))));

        root.then(Commands.literal("list").executes(MutagenCommand::list));

        root.then(Commands.literal("dose")
                .then(Commands.argument("strain", ResourceLocationArgument.id())
                        .suggests(STRAIN_SUGGESTIONS)
                        .executes(context -> dose(context, 100.0F))
                        .then(Commands.argument("quality", FloatArgumentType.floatArg(1.0F, 100.0F))
                                .executes(context -> dose(context,
                                        FloatArgumentType.getFloat(context, "quality"))))));

        root.then(Commands.literal("set")
                .then(Commands.argument("strain", ResourceLocationArgument.id())
                        .suggests(STRAIN_SUGGESTIONS)
                        .then(Commands.argument("progress", FloatArgumentType.floatArg(0.0F, 100.0F))
                                .executes(MutagenCommand::set))));

        root.then(Commands.literal("lock")
                .then(Commands.argument("locked", BoolArgumentType.bool())
                        .executes(MutagenCommand::lock)));

        root.then(Commands.literal("clear").executes(MutagenCommand::clear));

        root.then(Commands.literal("research")
                .executes(MutagenCommand::researchList)
                .then(Commands.argument("strain", ResourceLocationArgument.id())
                        .suggests(STRAIN_SUGGESTIONS)
                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0.0F, 100.0F))
                                .executes(MutagenCommand::researchSet))));

        dispatcher.register(root);
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        MutagenData data = MutagenPlayer.of(player);
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("mutagen.command.status.header", player.getDisplayName())
                .withStyle(ChatFormatting.AQUA), false);

        if (data.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mutagen.command.status.clean")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            Component name = strain != null ? strain.displayName() : Component.literal(gene.strainId().toString());
            Stage stage = gene.stage();
            source.sendSuccess(() -> Component.translatable("mutagen.command.status.gene",
                    name,
                    stage.displayName().copy().withStyle(stage.color()),
                    String.format("%.1f", gene.progress()),
                    String.format("%.1f", gene.pending()),
                    gene.locked() ? "on" : "off"), false);
        }
        BodyShape body = BodyShape.of(player);
        source.sendSuccess(() -> Component.translatable("mutagen.command.status.body",
                String.format("%.2f", body.width()), String.format("%.2f", body.height()),
                String.format("%.2f", body.eyeHeight()), String.format("%+.2f", body.reach()),
                Bodies.scaler().name()), false);
        source.sendSuccess(() -> Component.translatable("mutagen.command.status.humanity",
                String.format("%.1f", data.humanity())), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        if (Strains.count() == 0) {
            context.getSource().sendFailure(Component.translatable("mutagen.command.list.empty"));
            return 0;
        }
        for (Strain strain : Strains.all()) {
            context.getSource().sendSuccess(() -> Component.translatable("mutagen.command.list.entry",
                    Component.literal(strain.id().toString()).withStyle(ChatFormatting.YELLOW),
                    strain.displayName(),
                    strain.difficulty()), false);
        }
        return Strains.count();
    }

    private static int researchList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        Map<ResourceLocation, Float> known = MutagenKnowledge.get(source.getServer())
                .view(player == null ? null : player.getUUID());
        if (known.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mutagen.command.research.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (Map.Entry<ResourceLocation, Float> entry : known.entrySet()) {
            Strain strain = Strains.get(entry.getKey());
            Component name = strain != null ? strain.displayName() : Component.literal(entry.getKey().toString());
            float research = entry.getValue();
            source.sendSuccess(() -> Component.translatable("mutagen.command.research.entry", name,
                    String.format("%.1f", research), Research.serumLimit(research)), false);
        }
        return known.size();
    }

    private static int researchSet(CommandContext<CommandSourceStack> context) {
        Strain strain = strainArgument(context);
        if (strain == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        MutagenKnowledge knowledge = MutagenKnowledge.get(source.getServer());
        float value = knowledge.setResearch(player == null ? null : player.getUUID(), strain.id(),
                FloatArgumentType.getFloat(context, "percent"));
        knowledge.broadcast(source.getServer(), player == null ? null : player.getUUID(), strain.id());
        source.sendSuccess(() -> Component.translatable("mutagen.command.research.done",
                strain.displayName(), String.format("%.1f", value)), true);
        return 1;
    }

    private static int dose(CommandContext<CommandSourceStack> context, float quality) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Strain strain = strainArgument(context);
        if (strain == null) {
            return 0;
        }
        // Отладочная доза не упирается в изученность: ей нужно проверять трансформацию, а не науку.
        Mutation.dose(player, strain.id(), quality, GeneData.NO_LIMIT);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Strain strain = strainArgument(context);
        if (strain == null) {
            return 0;
        }
        float progress = FloatArgumentType.getFloat(context, "progress");

        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data.getOrCreate(strain.id());
        gene.setProgress(progress);
        gene.setPending(0.0F);
        data.markDirty();
        Mutation.refreshAttributes(player, data);
        MutagenNetwork.syncPlayer(player);

        context.getSource().sendSuccess(() -> Component.translatable("mutagen.command.set.done",
                strain.displayName(), String.format("%.1f", progress)), false);
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            context.getSource().sendFailure(Component.translatable("mutagen.command.status.clean"));
            return 0;
        }
        boolean locked = BoolArgumentType.getBool(context, "locked");
        gene.setLocked(locked);
        data.markDirty();
        MutagenNetwork.syncPlayer(player);
        context.getSource().sendSuccess(() -> Component.translatable(
                locked ? "mutagen.message.locked" : "mutagen.message.unlocked",
                Component.literal(gene.strainId().toString())), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MutagenData data = MutagenPlayer.of(player);
        data.clear();
        Mutation.clearAttributes(player);
        MutagenNetwork.syncPlayer(player);
        context.getSource().sendSuccess(() -> Component.translatable("mutagen.command.clear.done"), false);
        return 1;
    }

    private static Strain strainArgument(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "strain");
        Strain strain = Strains.get(id);
        // Короткая запись: /mutagen set zombie 50 вместо mutagen:zombie.
        if (strain == null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            strain = Strains.get(Mutagen.id(id.getPath()));
        }
        if (strain == null) {
            context.getSource().sendFailure(
                    Component.translatable("mutagen.command.unknown_strain", id.toString()));
        }
        return strain;
    }
}
