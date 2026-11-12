# Hook inventory

A map of every event NeoForge 26.3.0.101 fires from vanilla Minecraft code,
against what Weaver provides. NeoForge is used only as a reference for where
the game needs hooks: Weaver keeps its own API and does not copy NeoForge code
(NeoForge is LGPL-2.1; Weaver is LGPL-2.1-only).

## How this list was built

From NeoForge's patched Minecraft sources (`minecraft-patched-26.3.0.101-sources.jar`,
produced by a NeoForge MDK build):

1. Every call into `EventHooks`, `CommonHooks`, `ClientHooks`, or
   `NeoForge.EVENT_BUS.post` inside vanilla classes was collected: 536 call
   sites in 2,382 patched files.
2. Each helper was resolved to the event classes it constructs, from the
   NeoForge sources. 86 call sites call helpers that fire no event (behavior
   plumbing such as component validation); they are not listed.
3. The result is 267 distinct events and event phases (`.Pre`/`.Post` count
   separately). "Sites" is how many vanilla call sites fire it; "First site"
   is one vanilla class it is fired from.

The "900 hooks" figure often quoted for NeoForge counts patch hunks, most of
which add extension interfaces, capability plumbing, and registry support
rather than events.

## Summary

| Status | Events |
|---|---|
| Already in Weaver before this work (including 5 client events matched to existing registries in batch 6) | 53 |
| Added in batch 1 (`weaver.events.mixins.json`) | 23 |
| Added in batch 2: blocks and world | 16 |
| Added in batch 3: entities and combat | 24 |
| Added in batch 3b: equipment, armor, breathing, drowning, boss block breaking, spawn finalization | 6 |
| Added in batch 4a: player state, sleeping, stats, operators, tracking, player files, item expiry and pickup | 17 |
| Added in batch 4b: anvil, grindstone, smithing, brewing, cursor stacking, trading, item use on blocks, hotbar, hand swap | 12 |
| Added in batch 5: chunk data, tickets, and watching, game rules, spawn position, potential spawns, custom spawners, datapack sync, sleep, tags, vibration game events, sieges, enchanting levels, custom click actions | 17 |
| Added in batch 6a: client input, chat, login and logout, FOV, movement input, frames, window, shutdown, toasts, music and sounds, screen init, living entity rendering, mining clicks, difficulty, game mode, tags, pause, client settings | 32 |
| Added in batch 6b: interaction keys, empty clicks, frame end, mouse turning, camera distance, hearts, boss bars, screenshots, sound channels, item breaking, item frames, tooltip drawing, inventory effects, level render-state extraction | 17 |
| Added in the mob-griefing pass (`MobGriefingEvent`, all 38 vanilla reads of the rule) | 1 |
| Added as API features: server and client reload listeners, creative tab contents, particle providers, atlas stitching, model baking | 6 |
| Added as draw stages: the 9 level render stages, each a `LevelDrawRegistry` stage (`AfterOpaqueBlocks` and `AfterTranslucentParticles` got stages of their own after preview.2) | 9 |
| Added after the preview: the cancelable pause pre-event (`ClientPauseChangingEvent`) and `/tp` of any entity (`EntityTeleportEvent` with cause `COMMAND`) | 2 |
| Added in batch 7a: entity construction, size and section entry, visibility, effect particles, projectile lookup, XP orb targeting, spawn cluster size, equipment drop chance, enchanted block and entity loot levels, tree ground blocks, fluid-placed blocks, `/spreadplayers` teleports, mod mismatch on world load | 15 |
| Added in batch 7b: client render pipeline (debug subscriptions, section geometry, main render target stencil, block screen effects, frame graph passes, render buffers, effect screen tooltips) | 7 |
| Added in batch 7c: client registration (skull models, baking-result changes, fluid models, picture-in-picture renderers, render-state modifiers, texture atlases) and default components bound | 7 |
| Added in batch 7d: feature renderers for mod submit types, client data-generator registries, legacy structure conversions | 3 |
| Deferred | 0 |
| Total | 267 |

### Changes in 2.2.0-preview.2

Found while porting sixteen NeoForge mods to Weaver:

- `LivingDeathEvent` (`.living`) is the cancelable death event NeoForge mods
  expect; the older `LivingEntityDeathEvent` and `PlayerDeathEvent` stay
  observation-only. `PlayerRespawnedEvent` (`.player`) fires for the new player
  once `PlayerList#respawn` is done, matching NeoForge's
  `PlayerEvent.PlayerRespawnEvent`; Weaver's `PlayerRespawnEvent` is the
  earlier respawn-position lookup.
- `LivingDropsEvent` now holds a dying player's inventory and worn items, as
  NeoForge's does.
- `CriticalHitEvent` can change the critical damage factor
  (`setDamageMultiplier`).
- Three client hooks NeoForge fires from its own helpers, which the call-site
  scan above does not list: `ScreenOpeningEvent` (NeoForge
  `ScreenEvent.Opening`), `HudLayerRenderEvent` with `VanillaHudLayers` ids
  (`RenderGuiLayerEvent.Pre` for the vanilla layers), and `PipelineModifiers`
  (NeoForge's pipeline modifiers). (`ClientMouseButtonEvent`, NeoForge's
  `InputEvent.MouseButton`, came with the entity APIs before this.)

Several "existing" rows are equivalents rather than one-to-one matches (for
example `EntityHurtEvent` for `LivingIncomingDamageEvent`, and the client
registries for render and viewport events); the Weaver column names them.

## Events

| NeoForge event | Sites | First site | Status | Weaver |
|---|---|---|---|---|
| `AddClientReloadListenersEvent` | 1 | `client.Minecraft` | API features | `AddClientReloadListenersEvent` |
| `AddServerReloadListenersEvent` | 1 | `server.ReloadableServerResources` | API features | `AddServerReloadListenersEvent` |
| `BuildCreativeModeTabContentsEvent` | 1 | `world.item.CreativeModeTab` | API features | `BuildCreativeTabContentsEvent` |
| `DefaultDataComponentsBoundEvent` | 2 | `client.multiplayer.RegistryDataCollector` | batch 7c | `DefaultDataComponentsBoundEvent` |
| `EntityRenderersEvent.CreateSkullModels` | 1 | `client.Minecraft` | batch 7c | `CreateSkullModelsEvent` |
| `InitializeClientRegistriesEvent` | 1 | `client.data.Main` | batch 7d | `InitializeClientRegistriesEvent` |
| `ModMismatchEvent` | 1 | `world.level.storage.LevelStorageSource` | batch 7a | `ModMismatchEvent` |
| `ModelEvent.BakingCompleted` | 1 | `client.resources.model.ModelManager` | API features | `ModelBakingCompletedEvent` |
| `ModelEvent.ModifyBakingResult` | 1 | `client.resources.model.ModelManager` | batch 7c | `ModifyBakingResultEvent` |
| `RegisterFeatureRenderersEvent` | 1 | `client.renderer.feature.FeatureRenderDispatcher` | batch 7d | `RegisterFeatureRenderersEvent` |
| `RegisterFluidModelsEvent` | 1 | `client.renderer.block.FluidStateModelSet` | batch 7c | `RegisterFluidModelsEvent` |
| `RegisterParticleProvidersEvent` | 1 | `client.Minecraft` | API features | `RegisterParticleProvidersEvent` |
| `RegisterPictureInPictureRenderersEvent` | 1 | `client.renderer.GameRenderer` | batch 7c | `RegisterPictureInPictureRenderersEvent` |
| `RegisterRenderStateModifiersEvent` | 1 | `client.Minecraft` | batch 7c | `RegisterRenderStateModifiersEvent` |
| `RegisterStructureConversionsEvent` | 1 | `world.entity.monster.Enderman` | batch 7d | `RegisterStructureConversionsEvent` |
| `RegisterTextureAtlasesEvent` | 1 | `client.resources.model.sprite.AtlasManager` | batch 7c | `RegisterTextureAtlasesEvent` |
| `TextureAtlasStitchedEvent` | 1 | `client.renderer.texture.TextureAtlas` | API features | `TextureAtlasStitchedEvent` |
| `AlterGroundEvent` | 1 | `world.level.levelgen.feature.treedecorators.AlterGroundDecorator` | batch 7a | `AlterGroundEvent` |
| `BlockDropsEvent` | 1 | `world.level.block.Block` | batch 2 | `BlockDropsEvent` |
| `BlockEvent.FarmlandTrampleEvent` | 1 | `world.level.block.FarmlandBlock` | batch 2 | `FarmlandTrampleEvent` |
| `BlockEvent.FluidPlaceBlockEvent` | 1 | `world.level.material.LavaFluid` | batch 7a | `FluidPlaceBlockEvent` |
| `BlockEvent.PortalSpawnEvent` | 1 | `world.level.block.BaseFireBlock` | batch 2 | `PortalSpawnEvent` |
| `BlockGrowFeatureEvent` | 3 | `world.level.block.MushroomBlock` | batch 2 | `BlockGrowFeatureEvent` |
| `BonemealEvent` | 1 | `world.item.BoneMealItem` | batch 2 | `BonemealEvent` |
| `CreateFluidSourceEvent` | 1 | `world.level.material.FlowingFluid` | batch 2 | `CreateFluidSourceEvent` |
| `CropGrowEvent.Post` | 10 | `world.level.block.BambooStalkBlock` | batch 2 | `CropGrowPostEvent` |
| `CropGrowEvent.Pre` | 10 | `world.level.block.BambooStalkBlock` | batch 2 | `CropGrowPreEvent` |
| `EnchantedBlockLootEvent` | 2 | `world.level.storage.loot.functions.ApplyBonusCount` | batch 7a | `EnchantedBlockLootEvent` |
| `ExplosionEvent.Detonate` | 1 | `world.level.ServerExplosion` | batch 2 | `ExplosionDetonateEvent` |
| `ExplosionKnockbackEvent` | 1 | `world.level.ServerExplosion` | batch 2 | `ExplosionKnockbackEvent` |
| `NeighborNotifyEvent` | 3 | `server.level.ServerLevel` | batch 2 | `NeighborNotifyEvent` |
| `NoteBlockEvent.Change` | 1 | `world.level.block.NoteBlock` | batch 2 | `NoteBlockChangeEvent` |
| `PistonEvent.Post` | 1 | `world.level.block.piston.PistonBaseBlock` | batch 2 | `PistonMovePostEvent` |
| `PistonEvent.Pre` | 1 | `world.level.block.piston.PistonBaseBlock` | batch 2 | `PistonMovePreEvent` |
| `PotionBrewEvent.Post` | 1 | `world.level.block.entity.BrewingStandBlockEntity` | batch 2 | `PotionBrewPostEvent` |
| `PotionBrewEvent.Pre` | 1 | `world.level.block.entity.BrewingStandBlockEntity` | batch 2 | `PotionBrewPreEvent` |
| `ArmorHurtEvent` | 1 | `world.entity.LivingEntity` | batch 3b | `ArmorHurtEvent` |
| `ArrowLooseEvent` | 2 | `world.item.BowItem` | batch 3 | `ArrowLooseEvent` |
| `ArrowNockEvent` | 1 | `world.item.BowItem` | batch 3 | `ArrowNockEvent` |
| `CriticalHitEvent` | 1 | `world.entity.player.Player` | batch 3 | `CriticalHitEvent` |
| `EffectParticleModificationEvent` | 1 | `world.entity.LivingEntity` | batch 7a | `EffectParticleModificationEvent` |
| `EnchantedEntityLootEvent` | 2 | `world.level.storage.loot.functions.EnchantedCountIncreaseFunction` | batch 7a | `EnchantedEntityLootEvent` |
| `EndermanAngerEvent` | 1 | `world.entity.monster.Enderman` | batch 3 | `EndermanAngerEvent` |
| `EntityEvent.EnteringSection` | 2 | `world.level.entity.PersistentEntitySectionManager` | batch 7a | `EntityEnteringSectionEvent` |
| `EntityEvent.EntityConstructing` | 1 | `world.entity.Entity` | batch 7a | `EntityConstructingEvent` |
| `EntityEvent.Size` | 1 | `world.entity.Entity` | batch 7a | `EntitySizeEvent` |
| `EntityInvulnerabilityCheckEvent` | 1 | `world.entity.Entity` | batch 3 | `EntityInvulnerabilityCheckEvent` |
| `EntityMobGriefingEvent` | 22 | `server.level.ServerLevel` | mob-griefing pass | `MobGriefingEvent` (all 38 vanilla reads) |
| `EntityTeleportEvent.EnderEntity` | 2 | `world.entity.LivingEntity` | batch 3 | `EntityTeleportEvent (RANDOM)` |
| `EntityTeleportEvent.EnderPearl` | 1 | `world.entity.projectile.throwableitemprojectile.ThrownEnderpearl` | batch 3 | `EntityTeleportEvent (ENDER_PEARL)` |
| `EntityTeleportEvent.ItemConsumption` | 1 | `world.entity.LivingEntity` | batch 3 | `EntityTeleportEvent (RANDOM)` |
| `EntityTeleportEvent.SpreadPlayersCommand` | 1 | `server.commands.SpreadPlayersCommand` | batch 7a | `EntityTeleportEvent` (cause `SPREAD_PLAYERS`) |
| `EntityTravelToDimensionEvent` | 2 | `server.level.ServerPlayer` | batch 3 | `EntityTravelToDimensionEvent` |
| `FinalizeSpawnEvent` | 2 | `world.level.BaseSpawner` | batch 3b | `FinalizeSpawnEvent` |
| `GetEquipmentDropChanceEvent` | 1 | `world.item.enchantment.EnchantmentHelper` | batch 7a | `GetEquipmentDropChanceEvent` |
| `LivingBreatheEvent` | 1 | `world.entity.LivingEntity` | batch 3b | `LivingBreatheEvent` |
| `LivingChangeTargetEvent` | 2 | `world.entity.Mob` | batch 3 | `LivingChangeTargetEvent` |
| `LivingConversionEvent.Post` | 9 | `world.entity.ConversionTracker` | batch 3 | `LivingConversionPostEvent` |
| `LivingConversionEvent.Pre` | 9 | `world.entity.ConversionTracker` | batch 3 | `LivingConversionPreEvent` |
| `LivingDamageEvent.Post` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingDamagePostEvent` |
| `LivingDamageEvent.Pre` | 2 | `world.entity.LivingEntity` | batch 3 | `LivingDamagePreEvent` |
| `LivingDestroyBlockEvent` | 1 | `world.entity.boss.wither.WitherBoss` | batch 3b | `LivingDestroyBlockEvent` |
| `LivingDropsEvent` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingDropsEvent` |
| `LivingDrownEvent` | 1 | `world.entity.LivingEntity` | batch 3b | `LivingDrownEvent` |
| `LivingEntityUseItemEvent.Start` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingUseItemStartEvent` |
| `LivingEntityUseItemEvent.Tick` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingUseItemTickEvent` |
| `LivingEquipmentChangeEvent` | 1 | `world.entity.LivingEntity` | batch 3b | `LivingEquipmentChangeEvent` |
| `LivingEvent.LivingVisibilityEvent` | 1 | `world.entity.LivingEntity` | batch 7a | `LivingVisibilityEvent` |
| `LivingGetProjectileEvent` | 3 | `world.entity.LivingEntity` | batch 7a | `LivingGetProjectileEvent` |
| `LivingShieldBlockEvent` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingShieldBlockEvent` |
| `LivingSwapItemsEvent.Hands` | 2 | `server.network.ServerGamePacketListenerImpl` | batch 4b | `LivingSwapHandsEvent` |
| `LivingUseTotemEvent` | 1 | `world.entity.LivingEntity` | batch 3 | `LivingUseTotemEvent` |
| `MobDespawnEvent` | 2 | `world.entity.Mob` | batch 3 | `MobDespawnEvent` |
| `MobEffectEvent.Expired` | 1 | `world.entity.LivingEntity` | batch 3 | `MobEffectExpiredEvent` |
| `MobSplitEvent` | 1 | `world.entity.monster.cubemob.AbstractCubeMob` | batch 3 | `MobSplitEvent` |
| `ProjectileImpactEvent` | 7 | `world.entity.projectile.FireworkRocketEntity` | batch 3 | `ProjectileImpactEvent` |
| `SpawnClusterSizeEvent` | 1 | `world.level.NaturalSpawner` | batch 7a | `SpawnClusterSizeEvent` |
| `SweepAttackEvent` | 1 | `world.entity.player.Player` | batch 3 | `SweepAttackEvent` |
| `XpOrbTargetingEvent` | 1 | `world.entity.ExperienceOrb` | batch 7a | `XpOrbTargetingEvent` |
| `AnvilCraftEvent.Post` | 1 | `world.inventory.AnvilMenu` | batch 4b | `AnvilCraftPostEvent` |
| `AnvilCraftEvent.Pre` | 1 | `world.inventory.AnvilMenu` | batch 4b | `AnvilCraftPreEvent` |
| `CanContinueSleepingEvent` | 2 | `world.entity.LivingEntity` | batch 4a | `CanContinueSleepingEvent` |
| `CanPlayerSleepEvent` | 1 | `server.level.ServerPlayer` | batch 4a | `CanPlayerSleepEvent` |
| `GrindstoneEvent.OnPlaceItem` | 1 | `world.inventory.GrindstoneMenu` | batch 4b | `GrindstoneResultEvent` |
| `GrindstoneEvent.OnTakeItem` | 1 | `world.inventory.GrindstoneMenu` | batch 4b | `GrindstoneTakeEvent` |
| `ItemEntityPickupEvent.Post` | 1 | `world.entity.item.ItemEntity` | batch 4a | `ItemPickupPostEvent` |
| `ItemExpireEvent` | 1 | `world.entity.item.ItemEntity` | batch 4a | `ItemExpireEvent` |
| `ItemStackedOnOtherEvent` | 1 | `world.inventory.AbstractContainerMenu` | batch 4b | `ItemStackedOnOtherEvent` |
| `PermissionsChangedEvent` | 1 | `server.players.PlayerList` | batch 4a | `PermissionsChangedEvent` |
| `PlayerBrewedPotionEvent` | 1 | `world.inventory.BrewingStandMenu` | batch 4b | `PlayerBrewedPotionEvent` |
| `PlayerEvent.BreakSpeed` | 1 | `world.entity.player.Player` | batch 4a | `PlayerBreakSpeedEvent` |
| `PlayerEvent.HarvestCheck` | 2 | `world.entity.player.Player` | batch 4a | `PlayerHarvestCheckEvent` |
| `PlayerEvent.ItemSmithedEvent` | 1 | `world.inventory.SmithingMenu` | batch 4b | `ItemSmithedEvent` |
| `PlayerEvent.LoadFromFile` | 1 | `server.network.config.PrepareSpawnTask` | batch 4a | `PlayerLoadFromFileEvent` |
| `PlayerEvent.NameFormat` | 1 | `world.entity.player.Player` | batch 4a | `PlayerNameFormatEvent` |
| `PlayerEvent.SaveToFile` | 1 | `world.level.storage.PlayerDataStorage` | batch 4a | `PlayerSaveToFileEvent` |
| `PlayerEvent.StartTracking` | 1 | `server.level.ServerEntity` | batch 4a | `PlayerStartTrackingEvent` |
| `PlayerEvent.StopTracking` | 1 | `server.level.ServerEntity` | batch 4a | `PlayerStopTrackingEvent` |
| `PlayerEvent.TabListNameFormat` | 1 | `server.level.ServerPlayer` | batch 4a | `PlayerTabListNameFormatEvent` |
| `PlayerFlyableFallEvent` | 1 | `world.entity.player.Player` | batch 4a | `PlayerFlyableFallEvent` |
| `PlayerSetSpawnEvent` | 1 | `server.level.ServerPlayer` | batch 4a | `PlayerSetSpawnEvent` |
| `PlayerSpawnPhantomsEvent` | 1 | `world.level.levelgen.PhantomSpawner` | batch 4a | `PlayerSpawnPhantomsEvent` |
| `PlayerSwitchHotbarSlotEvent.Post` | 1 | `server.network.ServerGamePacketListenerImpl` | batch 4b | `PlayerSwitchHotbarSlotPostEvent` |
| `PlayerSwitchHotbarSlotEvent.Pre` | 1 | `server.network.ServerGamePacketListenerImpl` | batch 4b | `PlayerSwitchHotbarSlotPreEvent` |
| `StatAwardEvent` | 1 | `stats.StatsCounter` | batch 4a | `StatAwardEvent` |
| `TradeWithVillagerEvent` | 1 | `world.entity.npc.villager.AbstractVillager` | batch 4b | `TradeWithVillagerEvent` |
| `UseItemOnBlockEvent` | 2 | `world.item.ItemStack` | batch 4b | `UseItemOnBlockEvent` |
| `ChunkDataEvent.Load` | 1 | `server.level.ChunkMap` | batch 5 | `ChunkDataLoadEvent` |
| `ChunkDataEvent.Save` | 1 | `server.level.ChunkMap` | batch 5 | `ChunkDataSaveEvent` |
| `ChunkTicketLevelUpdatedEvent` | 1 | `server.level.ChunkMap` | batch 5 | `ChunkTicketLevelUpdatedEvent` |
| `ChunkWatchEvent.Sent` | 1 | `server.network.PlayerChunkSender` | batch 5 | `ChunkWatchEvent (SENT)` |
| `ChunkWatchEvent.UnWatch` | 1 | `server.level.ChunkMap` | batch 5 | `ChunkWatchEvent (UNWATCH)` |
| `ChunkWatchEvent.Watch` | 1 | `server.level.ChunkMap` | batch 5 | `ChunkWatchEvent (WATCH)` |
| `CustomClickActionEvent` | 1 | `server.MinecraftServer` | batch 5 | `CustomClickActionEvent` |
| `EnchantmentLevelSetEvent` | 1 | `world.inventory.EnchantmentMenu` | batch 5 | `EnchantmentLevelSetEvent` |
| `GameRuleChangedEvent` | 1 | `server.MinecraftServer` | batch 5 | `GameRuleChangedEvent` |
| `LevelEvent.CreateSpawnPosition` | 1 | `server.MinecraftServer` | batch 5 | `CreateSpawnPositionEvent` |
| `LevelEvent.PotentialSpawns` | 1 | `world.level.NaturalSpawner` | batch 5 | `PotentialSpawnsEvent` |
| `ModifyCustomSpawnersEvent` | 1 | `server.level.ServerLevel` | batch 5 | `ModifyCustomSpawnersEvent` |
| `OnDatapackSyncEvent` | 1 | `server.players.PlayerList` | batch 5 | `DatapackSyncEvent` |
| `SleepFinishedTimeEvent` | 1 | `server.level.ServerLevel` | batch 5 | `SleepFinishedEvent` |
| `TagsUpdatedEvent.ServerDataLoad` | 1 | `server.ReloadableServerResources` | batch 5 | `TagsUpdatedEvent` |
| `VanillaGameEvent` | 1 | `server.level.ServerLevel` | batch 5 | `VanillaGameEvent` |
| `VillageSiegeEvent` | 1 | `world.entity.ai.village.VillageSiege` | batch 5 | `VillageSiegeEvent` |
| `AddDebugSubscriptionFlagsEvent` | 1 | `client.multiplayer.ClientDebugSubscriber` | batch 7b | `AddDebugSubscriptionFlagsEvent` |
| `AddSectionGeometryEvent` | 1 | `client.renderer.extract.LevelExtractor` | batch 7b | `AddSectionGeometryEvent` |
| `CalculateDetachedCameraDistanceEvent` | 1 | `client.Camera` | batch 6b | `CameraDistanceEvent` |
| `CalculatePlayerTurnEvent` | 1 | `client.MouseHandler` | batch 6b | `PlayerTurnEvent` |
| `ClientChatEvent` | 1 | `client.multiplayer.ClientPacketListener` | batch 6a | `ClientChatSendEvent` |
| `ClientChatReceivedEvent` | 1 | `client.multiplayer.chat.ChatListener` | batch 6a | `ClientChatReceivedEvent` |
| `ClientChatReceivedEvent.Player` | 1 | `client.multiplayer.chat.ChatListener` | batch 6a | `ClientChatReceivedEvent (PLAYER)` |
| `ClientChatReceivedEvent.System` | 1 | `client.multiplayer.chat.ChatListener` | batch 6a | `ClientChatReceivedEvent (SYSTEM, OVERLAY)` |
| `ClientInformationUpdatedEvent` | 1 | `server.network.ServerGamePacketListenerImpl` | batch 6a | `ClientInformationUpdatedEvent` |
| `ClientPauseChangeEvent.Post` | 1 | `client.Minecraft` | batch 6a | `ClientPauseChangedEvent` |
| `ClientPauseChangeEvent.Pre` | 1 | `client.Minecraft` | added | `ClientPauseChangingEvent` (cancelable) |
| `ClientPlayerChangeGameTypeEvent` | 1 | `client.multiplayer.PlayerInfo` | batch 6a | `ClientGameModeChangeEvent` |
| `ClientPlayerNetworkEvent.Clone` | 1 | `client.multiplayer.ClientPacketListener` | batch 6a | `ClientPlayerNetworkEvent (RESPAWNED)` |
| `ClientPlayerNetworkEvent.LoggingIn` | 1 | `client.multiplayer.ClientPacketListener` | batch 6a | `ClientPlayerNetworkEvent (LOGGED_IN)` |
| `ClientPlayerNetworkEvent.LoggingOut` | 1 | `client.Minecraft` | batch 6a | `ClientPlayerNetworkEvent (LOGGED_OUT)` |
| `ComputeFovModifierEvent` | 1 | `client.player.AbstractClientPlayer` | batch 6a | `FovModifierEvent` |
| `ConfigureMainRenderTargetEvent` | 1 | `client.renderer.GameRenderer` | batch 7b | `ConfigureMainRenderTargetEvent` |
| `CustomizeGuiOverlayEvent.BossEventProgress` | 1 | `client.gui.components.BossHealthOverlay` | batch 6b | `BossBarRenderEvent` |
| `DifficultyChangeEvent` | 1 | `client.multiplayer.ClientLevel` | batch 6a | `ClientDifficultyChangeEvent` |
| `ExtractBlockScreenEffectEvent` | 1 | `client.renderer.extract.LevelExtractor` | batch 7b | `ExtractBlockScreenEffectEvent` |
| `ExtractLevelRenderStateEvent` | 1 | `client.renderer.extract.LevelExtractor` | batch 6b | `ExtractLevelRenderStateEvent` |
| `FlipFrameEvent` | 1 | `client.Minecraft` | batch 6b | `FlipFrameEvent` |
| `FrameGraphSetupEvent` | 1 | `client.renderer.LevelRenderer` | batch 7b | `FrameGraphSetupEvent` |
| `GameShuttingDownEvent` | 2 | `client.Minecraft` | batch 6a | `GameShuttingDownEvent` |
| `GatherEffectScreenTooltipsEvent` | 1 | `client.gui.screens.inventory.EffectsInInventory` | batch 7b | `GatherEffectScreenTooltipsEvent` |
| `InputEvent.InteractionKeyMappingTriggered` | 1 | `client.Minecraft` | batch 6b | `ClientInteractionInputEvent` |
| `InputEvent.Key` | 1 | `client.KeyboardHandler` | batch 6a | `ClientKeyInputEvent` |
| `InputEvent.MouseScrollingEvent` | 1 | `client.MouseHandler` | batch 6a | `ClientMouseScrollEvent` |
| `MovementInputUpdateEvent` | 1 | `client.player.LocalPlayer` | batch 6a | `MovementInputUpdateEvent` |
| `PlayLevelSoundEvent.AtEntity` | 3 | `client.multiplayer.ClientLevel` | batch 6a | `PlayLevelSoundEvent (entity)` |
| `PlayLevelSoundEvent.AtPosition` | 2 | `client.multiplayer.ClientLevel` | batch 6a | `PlayLevelSoundEvent (position)` |
| `PlaySoundEvent` | 1 | `client.sounds.SoundEngine` | batch 6a | `PlaySoundEvent` |
| `PlaySoundSourceEvent` | 1 | `client.sounds.SoundEngine` | batch 6b | `SoundSourceEvent` |
| `PlayStreamingSourceEvent` | 1 | `client.sounds.SoundEngine` | batch 6b | `SoundSourceEvent` |
| `PlayerDestroyItemEvent` | 6 | `client.multiplayer.MultiPlayerGameMode` | batch 6b | `PlayerItemBrokenEvent (durability breaks)` |
| `PlayerHeartTypeEvent` | 1 | `client.gui.Hud` | batch 6b | `HeartTypeEvent` |
| `PlayerInteractEvent.LeftClickBlock` | 3 | `client.multiplayer.MultiPlayerGameMode` | batch 6a | `ClientLeftClickBlockEvent` |
| `PlayerInteractEvent.LeftClickEmpty` | 1 | `client.Minecraft` | batch 6b | `ClientClickEmptyEvent (LEFT)` |
| `PlayerInteractEvent.RightClickEmpty` | 1 | `client.Minecraft` | batch 6b | `ClientClickEmptyEvent (RIGHT)` |
| `PrepareRenderBuffersEvent` | 1 | `client.renderer.LevelRenderer` | batch 7b | `PrepareRenderBuffersEvent` |
| `RenderArmEvent` | 1 | `client.renderer.FirstPersonHandsAndItemsRenderer` | existing | `HandRenderRegistry` |
| `RenderFrameEvent.Post` | 1 | `client.Minecraft` | batch 6a | `RenderFrameEvent (POST)` |
| `RenderFrameEvent.Pre` | 1 | `client.Minecraft` | batch 6a | `RenderFrameEvent (PRE)` |
| `RenderItemInFrameEvent` | 1 | `client.renderer.entity.ItemFrameRenderer` | batch 6b | `RenderItemInFrameEvent` |
| `RenderLevelStageEvent.AfterLevel` | 1 | `client.renderer.GameRenderer` | draw stages | `LevelDrawRegistry` `AFTER_LEVEL` |
| `RenderLevelStageEvent.AfterOpaqueBlocks` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_OPAQUE_BLOCKS` |
| `RenderLevelStageEvent.AfterOpaqueFeatures` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_SOLID` |
| `RenderLevelStageEvent.AfterSky` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_SKY` |
| `RenderLevelStageEvent.AfterTranslucentBlocks` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_TRANSLUCENT_BLOCKS` |
| `RenderLevelStageEvent.AfterTranslucentFeatures` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_TRANSLUCENT_FEATURES` |
| `RenderLevelStageEvent.AfterTranslucentParticles` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_PARTICLES` |
| `RenderLevelStageEvent.AfterWeather` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` `AFTER_WEATHER` |
| `RenderLevelStageEvent.OitTranslucent` | 1 | `client.renderer.LevelRenderer` | draw stages | `LevelDrawRegistry` translucent stages with `oitLayer` |
| `RenderLivingEvent.Post` | 1 | `client.renderer.entity.LivingEntityRenderer` | batch 6a | `RenderLivingEvent (POST)` |
| `RenderLivingEvent.Pre` | 1 | `client.renderer.entity.LivingEntityRenderer` | batch 6a | `RenderLivingEvent (PRE)` |
| `RenderPlayerEvent.Post` | 1 | `client.renderer.entity.player.AvatarRenderer` | batch 6a | `RenderLivingEvent (POST, AvatarRenderState)` |
| `RenderPlayerEvent.Pre` | 1 | `client.renderer.entity.player.AvatarRenderer` | batch 6a | `RenderLivingEvent (PRE, AvatarRenderState)` |
| `RenderTooltipEvent.GatherComponents` | 1 | `client.gui.GuiGraphicsExtractor` | existing | `TooltipRegistry` |
| `RenderTooltipEvent.Pre` | 1 | `client.gui.GuiGraphicsExtractor` | batch 6b | `RenderTooltipEvent (PRE)` |
| `RenderTooltipEvent.Texture` | 1 | `client.gui.GuiGraphicsExtractor` | batch 6b | `RenderTooltipEvent (BACKGROUND)` |
| `ScreenEvent.Init.Post` | 1 | `client.gui.screens.Screen` | batch 6a | `ScreenInitEvent (POST)` |
| `ScreenEvent.Init.Pre` | 1 | `client.gui.screens.Screen` | batch 6a | `ScreenInitEvent (PRE)` |
| `ScreenEvent.Render.Background` | 1 | `client.gui.screens.Screen` | existing | `ScreenRenderRegistry (BACKGROUND)` |
| `ScreenEvent.Render.Foreground` | 3 | `client.gui.screens.Screen` | existing | `ScreenRenderRegistry (POST)` |
| `ScreenEvent.RenderInventoryMobEffects` | 1 | `client.gui.screens.inventory.EffectsInInventory` | batch 6b | `InventoryEffectsRenderEvent` |
| `ScreenshotEvent` | 1 | `client.Screenshot` | batch 6b | `ScreenshotEvent` |
| `SelectMusicEvent` | 1 | `client.sounds.MusicManager` | batch 6a | `SelectMusicEvent` |
| `SubmitCustomGeometryEvent` | 1 | `client.renderer.LevelRenderer` | existing | `LevelRenderRegistry (ENTITIES) and WorldRenderRegistry` |
| `TagsUpdatedEvent.ClientPacketReceived` | 2 | `client.multiplayer.ClientConfigurationPacketListenerImpl` | batch 6a | `ClientTagsUpdatedEvent` |
| `ToastAddEvent` | 1 | `client.gui.components.toasts.ToastManager` | batch 6a | `ToastAddEvent` |
| `WindowResizeEvent` | 1 | `client.Minecraft` | batch 6a | `WindowResizeEvent` |
| `AdvancementEarnEvent` | 1 | `server.PlayerAdvancements` | batch1 | `AdvancementProgressEvent (completed=true)` |
| `AdvancementProgressEvent` | 1 | `server.PlayerAdvancements` | batch1 | `AdvancementProgressEvent` |
| `AnimalTameEvent` | 5 | `world.entity.ai.goal.RunAroundLikeCrazyGoal` | batch1 | `AnimalTameEvent` |
| `EntityMountEvent` | 1 | `world.entity.Entity` | batch1 | `EntityMountEvent` |
| `EntityStruckByLightningEvent` | 1 | `world.entity.LightningBolt` | batch1 | `EntityStruckByLightningEvent` |
| `ExplosionEvent.Start` | 1 | `server.level.ServerLevel` | batch1 | `ExplosionStartEvent` |
| `ItemEntityPickupEvent.Pre` | 1 | `world.entity.item.ItemEntity` | batch1 | `ItemPickupEvent` |
| `ItemTossEvent` | 1 | `world.entity.player.Player` | batch1 | `ItemTossEvent` |
| `LivingEvent.LivingJumpEvent` | 5 | `world.entity.LivingEntity` | batch1 | `LivingJumpEvent` |
| `LivingExperienceDropEvent` | 2 | `world.entity.LivingEntity` | batch1 | `LivingExperienceDropEvent` |
| `LivingFallEvent` | 1 | `world.entity.LivingEntity` | batch1 | `LivingFallEvent` |
| `LivingHealEvent` | 1 | `world.entity.LivingEntity` | batch1 | `LivingHealEvent` |
| `LivingKnockBackEvent` | 1 | `world.entity.LivingEntity` | batch1 | `LivingKnockBackEvent` |
| `MobEffectEvent.Added` | 1 | `world.entity.LivingEntity` | batch1 | `MobEffectAddedEvent` |
| `MobEffectEvent.Applicable` | 1 | `world.entity.LivingEntity` | batch1 | `MobEffectApplicableEvent` |
| `MobEffectEvent.Remove` | 1 | `world.entity.LivingEntity` | batch1 | `MobEffectRemoveEvent` |
| `PlayerEvent.Clone` | 1 | `server.level.ServerPlayer` | batch1 | `PlayerCloneEvent` |
| `PlayerEvent.ItemCraftedEvent` | 1 | `world.inventory.ResultSlot` | batch1 | `ItemCraftedEvent` |
| `PlayerEvent.ItemSmeltedEvent` | 1 | `world.inventory.FurnaceResultSlot` | batch1 | `ItemSmeltedEvent` |
| `PlayerEvent.PlayerChangeGameModeEvent` | 1 | `server.level.ServerPlayer` | batch1 | `PlayerChangeGameModeEvent` |
| `PlayerEvent.PlayerChangedDimensionEvent` | 1 | `server.level.ServerPlayer` | batch1 | `PlayerChangedDimensionEvent` |
| `PlayerWakeUpEvent` | 1 | `world.entity.player.Player` | batch1 | `PlayerWakeUpEvent` |
| `PlayerXpEvent.PickupXp` | 1 | `world.entity.ExperienceOrb` | batch1 | `ExperiencePickupEvent` |
| `AnvilUpdateEvent` | 1 | `world.inventory.AnvilMenu` | existing | `AnvilUpdateEvent` |
| `AttackEntityEvent` | 1 | `world.entity.player.Player` | existing | `EntityAttackEvent` |
| `BlockEvent.EntityPlaceEvent` | 1 | `world.entity.monster.Enderman` | existing | `BlockPlaceEvent (result override)` |
| `BreakBlockEvent` | 2 | `client.multiplayer.MultiPlayerGameMode` | existing | `BlockBreakEvent (cancelable)` |
| `ChunkEvent.Load` | 2 | `client.multiplayer.ClientChunkCache` | existing | `ChunkLoadEvent` |
| `ChunkEvent.Unload` | 2 | `client.multiplayer.ClientChunkCache` | existing | `ChunkUnloadEvent` |
| `ClientResourceLoadFinishedEvent` | 1 | `client.Minecraft` | existing | `ResourceReloadEvent` |
| `ClientTickEvent.Post` | 1 | `client.Minecraft` | existing | `ClientTickEvent` |
| `ClientTickEvent.Pre` | 1 | `client.Minecraft` | existing | `ClientTickEvent` |
| `EntityJoinLevelEvent` | 3 | `client.multiplayer.ClientLevel` | existing | `EntityJoinLevelEvent` |
| `EntityLeaveLevelEvent` | 2 | `client.multiplayer.ClientLevel` | existing | `EntityRemovalEvent` |
| `EntityRenderersEvent.RegisterLayerDefinitions` | 1 | `client.Minecraft` | existing | `client model-layer registry` |
| `EntityRenderersEvent.RegisterRenderers` | 1 | `client.Minecraft` | existing | `client entity-renderer registry` |
| `EntityTickEvent.Post` | 3 | `client.multiplayer.ClientLevel` | existing | `EntityTickEvent` |
| `EntityTickEvent.Pre` | 3 | `client.multiplayer.ClientLevel` | existing | `EntityTickEvent` |
| `ItemTooltipEvent` | 1 | `world.item.ItemStack` | existing | `ClientRegistry tooltip providers` |
| `LevelEvent.Load` | 2 | `client.multiplayer.ClientLevel` | existing | `LevelLoadEvent` |
| `LevelEvent.Save` | 1 | `server.level.ServerLevel` | existing | `LevelSaveEvent` |
| `LevelEvent.Unload` | 2 | `client.Minecraft` | existing | `LevelUnloadEvent` |
| `LevelTickEvent.Post` | 2 | `client.Minecraft` | existing | `LevelTickEvent` |
| `LevelTickEvent.Pre` | 2 | `client.Minecraft` | existing | `LevelTickEvent` |
| `LivingDeathEvent` | 3 | `server.level.ServerPlayer` | existing | `LivingDeathEvent` (cancelable, since 2.3.2) / `LivingEntityDeathEvent / PlayerDeathEvent` |
| `LivingEntityUseItemEvent.Finish` | 1 | `world.entity.LivingEntity` | existing | `ItemUseFinishEvent` |
| `LivingEntityUseItemEvent.Stop` | 1 | `world.entity.LivingEntity` | existing | `ItemReleaseEvent` |
| `LivingIncomingDamageEvent` | 1 | `world.entity.LivingEntity` | existing | `EntityHurtEvent (cancelable)` |
| `LootTableLoadEvent` | 1 | `resources.RegistryLoadTask` | existing | `LootTableEvent` |
| `PlayerContainerEvent.Close` | 1 | `server.level.ServerPlayer` | existing | `ContainerCloseEvent / ContainerClosedEvent` |
| `PlayerContainerEvent.Open` | 1 | `server.level.ServerPlayer` | existing | `ContainerOpenEvent` |
| `PlayerEnchantItemEvent` | 1 | `world.inventory.EnchantmentMenu` | existing | `EnchantEvent` |
| `PlayerEvent.PlayerLoggedInEvent` | 1 | `server.players.PlayerList` | existing | `PlayerLoginEvent / PlayerJoinEvent` |
| `PlayerEvent.PlayerLoggedOutEvent` | 1 | `server.players.PlayerList` | existing | `PlayerLogoutEvent / PlayerLeaveEvent` |
| `PlayerEvent.PlayerRespawnEvent` | 1 | `server.players.PlayerList` | existing | `PlayerRespawnedEvent` (since 2.3.2) / `PlayerRespawnEvent / PlayerRespawnActionEvent` |
| `PlayerInteractEvent.EntityInteract` | 1 | `world.entity.player.Player` | existing | `EntityInteractEvent` |
| `PlayerInteractEvent.RightClickBlock` | 2 | `client.multiplayer.MultiPlayerGameMode` | existing | `BlockInteractEvent` |
| `PlayerInteractEvent.RightClickItem` | 2 | `client.multiplayer.MultiPlayerGameMode` | existing | `ItemUseEvent` |
| `PlayerRespawnPositionEvent` | 1 | `server.players.PlayerList` | existing | `PlayerRespawnTargetEvent` |
| `PlayerTickEvent.Post` | 1 | `world.entity.player.Player` | existing | `PlayerTickEvent / ClientPlayerTickEvent` |
| `PlayerTickEvent.Pre` | 1 | `world.entity.player.Player` | existing | `PlayerTickEvent / ClientPlayerTickEvent` |
| `RegisterCommandsEvent` | 1 | `commands.Commands` | existing | `CommandRegisterEvent` |
| `RegisterKeyMappingsEvent` | 1 | `client.Options` | existing | `ClientRegistry key mappings` |
| `RenderHandEvent` | 1 | `client.renderer.FirstPersonHandsAndItemsRenderer` | existing | `client hand registry` |
| `ScreenEvent.Closing` | 1 | `client.gui.Gui` | existing | `ScreenCloseEvent` |
| `ServerTickEvent.Post` | 1 | `server.MinecraftServer` | existing | `ServerTickEvent` |
| `ServerTickEvent.Pre` | 1 | `server.MinecraftServer` | existing | `ServerTickEvent` |
| `ViewportEvent.ComputeCameraAngles` | 1 | `client.Camera` | existing | `client camera registry` |
| `ViewportEvent.ComputeFogColor` | 1 | `client.renderer.fog.FogRenderer` | existing | `client fog registry` |
| `ViewportEvent.ComputeFov` | 1 | `client.Camera` | existing | `client FOV registry` |
| `ViewportEvent.RenderFog` | 1 | `client.renderer.fog.FogRenderer` | existing | `client fog registry` |
| `EntityTeleportEvent.TeleportCommand` | 1 | `server.commands.TeleportCommand` | added | `EntityTeleportEvent` cause `COMMAND` (every entity) |
