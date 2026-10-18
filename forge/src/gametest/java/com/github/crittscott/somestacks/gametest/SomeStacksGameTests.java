package com.github.crittscott.somestacks.gametest;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/** Development-only test functions; datapack test instances supply their structure and timing. */
@Mod(SomeStacksGameTests.MOD_ID)
public final class SomeStacksGameTests {
    public static final String MOD_ID = "somestacks_gametest";
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "somestacks");

    static {
        TEST_FUNCTIONS.register("barcolumngametests/validity_and_bottom_grounding_are_enforced",
                () -> BarColumnGameTests::validityAndBottomGroundingAreEnforced);
        TEST_FUNCTIONS.register("barcolumngametests/bricks_are_bar_items_but_brick_blocks_are_not",
                () -> BarColumnGameTests::bricksAreBarItemsButBrickBlocksAreNot);
        TEST_FUNCTIONS.register("barcolumngametests/upper_bar_requires_overlapping_support",
                () -> BarColumnGameTests::upperBarRequiresOverlappingSupport);
        TEST_FUNCTIONS.register("barcolumngametests/player_extraction_drops_unsupported_bars",
                () -> BarColumnGameTests::playerExtractionDropsUnsupportedBars);
        TEST_FUNCTIONS.register("barcolumngametests/supported_neighbor_survives_player_extraction",
                () -> BarColumnGameTests::supportedNeighborSurvivesPlayerExtraction);
        TEST_FUNCTIONS.register("barcolumngametests/differently_oriented_seam_uses_footprint_overlap",
                () -> BarColumnGameTests::differentlyOrientedSeamUsesFootprintOverlap);
        TEST_FUNCTIONS.register("barcolumngametests/deposit_trace_stops_at_last_empty_before_occupied_bar",
                () -> BarColumnGameTests::depositTraceStopsAtLastEmptyBeforeOccupiedBar);
        TEST_FUNCTIONS.register("barcolumngametests/automation_backfills_without_dropping",
                () -> BarColumnGameTests::automationBackfillsWithoutDropping);
        TEST_FUNCTIONS.register("barcolumngametests/capability_insertion_answers_for_the_position_it_is_given",
                () -> BarColumnGameTests::capabilityInsertionAnswersForThePositionItIsGiven);
        TEST_FUNCTIONS.register("barcolumngametests/backfill_keeps_a_bar_it_would_no_longer_accept",
                () -> BarColumnGameTests::backfillKeepsABarItWouldNoLongerAccept);
        TEST_FUNCTIONS.register("barcolumngametests/capability_simulation_does_not_mutate_extraction",
                () -> BarColumnGameTests::capabilitySimulationDoesNotMutateExtraction);
        TEST_FUNCTIONS.register("barcolumngametests/breaking_lower_block_collapses_dependent_upper_block",
                () -> BarColumnGameTests::breakingLowerBlockCollapsesDependentUpperBlock);
        TEST_FUNCTIONS.register("barcolumngametests/breaking_full_column_consolidates_drops",
                () -> BarColumnGameTests::breakingFullColumnConsolidatesDrops);
        TEST_FUNCTIONS.register("barcolumngametests/player_cascade_consolidates_across_blocks",
                () -> BarColumnGameTests::playerCascadeConsolidatesAcrossBlocks);
        TEST_FUNCTIONS.register("barcolumngametests/collapse_does_not_merge_different_components",
                () -> BarColumnGameTests::collapseDoesNotMergeDifferentComponents);
        TEST_FUNCTIONS.register("barcolumngametests/comparator_reserves_zero_for_an_empty_column",
                () -> BarColumnGameTests::comparatorReservesZeroForAnEmptyColumn);
        TEST_FUNCTIONS.register("barcolumngametests/comparator_reads_the_whole_column_from_every_block",
                () -> BarColumnGameTests::comparatorReadsTheWholeColumnFromEveryBlock);
        TEST_FUNCTIONS.register("barcolumngametests/comparator_follows_a_column_losing_a_block",
                () -> BarColumnGameTests::comparatorFollowsAColumnLosingABlock);
        TEST_FUNCTIONS.register("barcolumngametests/refused_removal_still_lets_the_bars_above_come_down",
                () -> BarColumnGameTests::refusedRemovalStillLetsTheBarsAboveComeDown);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/item_capability_is_available_from_every_side",
                () -> CapabilityAndPersistenceGameTests::itemCapabilityIsAvailableFromEverySide);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/single_block_capabilities_advertise_configured_headroom",
                () -> CapabilityAndPersistenceGameTests::singleBlockCapabilitiesAdvertiseConfiguredHeadroom);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/storage_disk_save_round_trips_items_rotation_and_permanence",
                () -> CapabilityAndPersistenceGameTests::storageDiskSaveRoundTripsItemsRotationAndPermanence);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/singles_disk_save_round_trips_items_and_both_rotations",
                () -> CapabilityAndPersistenceGameTests::singlesDiskSaveRoundTripsItemsAndBothRotations);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/bar_disk_save_round_trips_items",
                () -> CapabilityAndPersistenceGameTests::barDiskSaveRoundTripsItems);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/update_tags_carry_render_state_and_omit_server_only_data",
                () -> CapabilityAndPersistenceGameTests::updateTagsCarryRenderStateAndOmitServerOnlyData);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/unreadable_saved_items_are_kept_aside_on_disk",
                () -> CapabilityAndPersistenceGameTests::unreadableSavedItemsAreKeptAsideOnDisk);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/set_aside_items_recover_into_free_slots",
                () -> CapabilityAndPersistenceGameTests::setAsideItemsRecoverIntoFreeSlots);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/previous_release_save_upgrades_on_load",
                () -> CapabilityAndPersistenceGameTests::previousReleaseSaveUpgradesOnLoad);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/structure_rotation_transforms_rotatable_stack_facing",
                () -> CapabilityAndPersistenceGameTests::structureRotationTransformsRotatableStackFacing);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/cached_shapes_invalidate_when_contents_change",
                () -> CapabilityAndPersistenceGameTests::cachedShapesInvalidateWhenContentsChange);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/reentrant_structural_edit_is_refused",
                () -> CapabilityAndPersistenceGameTests::reentrantStructuralEditIsRefused);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/capability_simulation_does_not_mutate_any_stack",
                () -> CapabilityAndPersistenceGameTests::capabilitySimulationDoesNotMutateAnyStack);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/client_inventory_updates_reject_malformed_data_atomically",
                () -> CapabilityAndPersistenceGameTests::clientInventoryUpdatesRejectMalformedDataAtomically);
        TEST_FUNCTIONS.register("capabilityandpersistencegametests/client_singles_updates_validate_rotation_before_inventory",
                () -> CapabilityAndPersistenceGameTests::clientSinglesUpdatesValidateRotationBeforeInventory);
        TEST_FUNCTIONS.register("configurationgametests/override_json_round_trips_valid_fields_and_skips_malformed_ones",
                () -> ConfigurationGameTests::overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes);
        TEST_FUNCTIONS.register("configurationgametests/override_json_uses_inclusive_bounds",
                () -> ConfigurationGameTests::overrideJsonUsesInclusiveBounds);
        TEST_FUNCTIONS.register("configurationgametests/deny_policies_respect_player_automation_and_existing_contents",
                () -> ConfigurationGameTests::denyPoliciesRespectPlayerAutomationAndExistingContents);
        TEST_FUNCTIONS.register("configurationgametests/disabled_types_and_height_limits_refuse_placement_and_growth_atomically",
                () -> ConfigurationGameTests::disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically);
        TEST_FUNCTIONS.register("configurationgametests/server_commands_enforce_permissions_and_persist_edits",
                () -> ConfigurationGameTests::serverCommandsEnforcePermissionsAndPersistEdits);
        TEST_FUNCTIONS.register("configurationgametests/ingot_tag_controls_bar_and_singles_admission",
                () -> ConfigurationGameTests::ingotTagControlsBarAndSinglesAdmission);
        TEST_FUNCTIONS.register("configurationgametests/native_server_policy_persists_command_lists",
                () -> ConfigurationGameTests::nativeServerPolicyPersistsCommandLists);
        TEST_FUNCTIONS.register("interactiongametests/malformed_gesture_state_fails_during_decoding",
                () -> InteractionGameTests::malformedGestureStateFailsDuringDecoding);
        TEST_FUNCTIONS.register("interactiongametests/gesture_state_tracks_mode_and_modifier",
                () -> InteractionGameTests::gestureStateTracksModeAndModifier);
        TEST_FUNCTIONS.register("interactiongametests/interaction_reads_only_the_main_hand",
                () -> InteractionGameTests::interactionReadsOnlyTheMainHand);
        TEST_FUNCTIONS.register("interactiongametests/rotation_validates_held_item_and_block_type",
                () -> InteractionGameTests::rotationValidatesHeldItemAndBlockType);
        TEST_FUNCTIONS.register("interactiongametests/sneaking_rotations_reach_the_loader_hook",
                () -> InteractionGameTests::sneakingRotationsReachTheLoaderHook);
        TEST_FUNCTIONS.register("interactiongametests/vanilla_interaction_reaches_deposit_extract_and_placement",
                () -> InteractionGameTests::vanillaInteractionReachesDepositExtractAndPlacement);
        TEST_FUNCTIONS.register("interactiongametests/item_rotation_targets_only_an_occupied_cell",
                () -> InteractionGameTests::itemRotationTargetsOnlyAnOccupiedCell);
        TEST_FUNCTIONS.register("interactiongametests/permanence_requires_empty_hand_and_honors_protection",
                () -> InteractionGameTests::permanenceRequiresEmptyHandAndHonorsProtection);
        TEST_FUNCTIONS.register("interactiongametests/rotation_sound_throttle_suppresses_same_tick_and_clears",
                () -> InteractionGameTests::rotationSoundThrottleSuppressesSameTickAndClears);
        TEST_FUNCTIONS.register("interactiongametests/player_mutation_emits_block_change",
                () -> InteractionGameTests::playerMutationEmitsBlockChange);
        TEST_FUNCTIONS.register("interactiongametests/extraction_refuses_incompatible_or_full_hands",
                () -> InteractionGameTests::extractionRefusesIncompatibleOrFullHands);
        TEST_FUNCTIONS.register("interactiongametests/full_top_face_places_the_selected_stack_type",
                () -> InteractionGameTests::fullTopFacePlacesTheSelectedStackType);
        TEST_FUNCTIONS.register("interactiongametests/every_stack_type_blocks_pistons",
                () -> InteractionGameTests::everyStackTypeBlocksPistons);
        TEST_FUNCTIONS.register("interactiongametests/extraction_events_follow_block_survival",
                () -> InteractionGameTests::extractionEventsFollowBlockSurvival);
        TEST_FUNCTIONS.register("interactiongametests/config_decoder_enforces_packet_budget",
                () -> InteractionGameTests::configDecoderEnforcesPacketBudget);
        TEST_FUNCTIONS.register("interactiongametests/config_staging_rejects_and_clears_invalid_generations",
                () -> InteractionGameTests::configStagingRejectsAndClearsInvalidGenerations);
        TEST_FUNCTIONS.register("interactiongametests/config_snapshots_round_trip_and_publish_atomically",
                () -> InteractionGameTests::configSnapshotsRoundTripAndPublishAtomically);
        TEST_FUNCTIONS.register("interactiongametests/config_staging_checks_sequences_and_totals",
                () -> InteractionGameTests::configStagingChecksSequencesAndTotals);
        TEST_FUNCTIONS.register("interactiongametests/config_decoder_rejects_malformed_counts_and_namespaces",
                () -> InteractionGameTests::configDecoderRejectsMalformedCountsAndNamespaces);
        TEST_FUNCTIONS.register("interactiongametests/block_rules_respect_precedence_and_hands",
                () -> InteractionGameTests::blockRulesRespectPrecedenceAndHands);
        TEST_FUNCTIONS.register("interactiongametests/full_top_faces_fall_through_to_placement",
                () -> InteractionGameTests::fullTopFacesFallThroughToPlacement);
        TEST_FUNCTIONS.register("interactiongametests/air_rules_and_disabled_mode_cycling",
                () -> InteractionGameTests::airRulesAndDisabledModeCycling);
        TEST_FUNCTIONS.register("interactiongametests/gesture_sync_sends_changes_and_resets",
                () -> InteractionGameTests::gestureSyncSendsChangesAndResets);
        TEST_FUNCTIONS.register("itemopsgametests/hand_compatibility_uses_exact_item_components_and_capacity",
                () -> ItemOpsGameTests::handCompatibilityUsesExactItemComponentsAndCapacity);
        TEST_FUNCTIONS.register("itemopsgametests/merge_returns_moved_count_and_does_not_shrink_incoming",
                () -> ItemOpsGameTests::mergeReturnsMovedCountAndDoesNotShrinkIncoming);
        TEST_FUNCTIONS.register("itemopsgametests/light_is_per_occupied_slot_integer_divided_and_capped",
                () -> ItemOpsGameTests::lightIsPerOccupiedSlotIntegerDividedAndCapped);
        TEST_FUNCTIONS.register("protectiongametests/automation_uses_shared_identity",
                () -> ProtectionGameTests::automationUsesSharedIdentity);
        TEST_FUNCTIONS.register("protectiongametests/automation_actor_discards_client_messages",
                () -> ProtectionGameTests::automationActorDiscardsClientMessages);
        TEST_FUNCTIONS.register("protectiongametests/checked_placement_places_in_bounds_and_rejects_outside_build_height",
                () -> ProtectionGameTests::checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight);
        TEST_FUNCTIONS.register("protectiongametests/checked_placement_rejects_an_obstructing_entity",
                () -> ProtectionGameTests::checkedPlacementRejectsAnObstructingEntity);
        TEST_FUNCTIONS.register("protectiongametests/creative_deposit_fills_the_stack_without_spending_the_hand",
                () -> ProtectionGameTests::creativeDepositFillsTheStackWithoutSpendingTheHand);
        TEST_FUNCTIONS.register("protectiongametests/player_placement_and_growth_award_placed_block",
                () -> ProtectionGameTests::playerPlacementAndGrowthAwardPlacedBlock);
        TEST_FUNCTIONS.register("protectiongametests/settle_keeps_an_empty_top_block_whose_removal_is_refused",
                () -> ProtectionGameTests::settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused);
        TEST_FUNCTIONS.register("protectiongametests/player_extraction_uses_player_for_cleanup",
                () -> ProtectionGameTests::playerExtractionUsesPlayerForCleanup);
        TEST_FUNCTIONS.register("protectiongametests/player_storage_settlement_uses_player_for_cleanup",
                () -> ProtectionGameTests::playerStorageSettlementUsesPlayerForCleanup);
        TEST_FUNCTIONS.register("protectiongametests/placement_into_water_keeps_the_water",
                () -> ProtectionGameTests::placementIntoWaterKeepsTheWater);
        TEST_FUNCTIONS.register("protectiongametests/adjacent_stack_consults_destination_protection",
                () -> ProtectionGameTests::adjacentStackConsultsDestinationProtection);
        TEST_FUNCTIONS.register("protectiongametests/adjacent_deposits_fill_grounded_cells",
                () -> ProtectionGameTests::adjacentDepositsFillGroundedCells);
        TEST_FUNCTIONS.register("protectiongametests/placement_publication_answers_to_veto",
                () -> ProtectionGameTests::placementPublicationAnswersToVeto);
        TEST_FUNCTIONS.register("protectiongametests/player_bar_extraction_uses_player_for_cleanup",
                () -> ProtectionGameTests::playerBarExtractionUsesPlayerForCleanup);
        TEST_FUNCTIONS.register("protectiongametests/mixed_storage_settlement_uses_automation_for_cleanup",
                () -> ProtectionGameTests::mixedStorageSettlementUsesAutomationForCleanup);
        TEST_FUNCTIONS.register("protectiongametests/vetoed_placement_restores_block_entity",
                () -> ProtectionGameTests::vetoedPlacementRestoresBlockEntity);
        TEST_FUNCTIONS.register("protectiongametests/storage_growth_answers_to_protection_in_simulation_and_commit",
                () -> ProtectionGameTests::storageGrowthAnswersToProtectionInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/singles_growth_answers_to_protection_in_simulation_and_commit",
                () -> ProtectionGameTests::singlesGrowthAnswersToProtectionInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/bar_growth_answers_to_protection_in_simulation_and_commit",
                () -> ProtectionGameTests::barGrowthAnswersToProtectionInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/storage_growth_rejects_an_obstructing_entity_in_simulation_and_commit",
                () -> ProtectionGameTests::storageGrowthRejectsAnObstructingEntityInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/singles_growth_rejects_an_obstructing_entity_in_simulation_and_commit",
                () -> ProtectionGameTests::singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/bar_growth_rejects_an_obstructing_entity_in_simulation_and_commit",
                () -> ProtectionGameTests::barGrowthRejectsAnObstructingEntityInSimulationAndCommit);
        TEST_FUNCTIONS.register("protectiongametests/denied_cleanup_emits_only_block_change",
                () -> ProtectionGameTests::deniedCleanupEmitsOnlyBlockChange);
        TEST_FUNCTIONS.register("rendergallerygametests/queued_storage_gallery_builds_floor_rows_and_completion_totals",
                () -> RenderGalleryGameTests::queuedStorageGalleryBuildsFloorRowsAndCompletionTotals);
        TEST_FUNCTIONS.register("rendergallerygametests/queued_storage_gallery_spreads_work_across_ticks",
                () -> RenderGalleryGameTests::queuedStorageGallerySpreadsWorkAcrossTicks);
        TEST_FUNCTIONS.register("rendergallerygametests/queued_bar_gallery_builds_rows_and_fills_bars",
                () -> RenderGalleryGameTests::queuedBarGalleryBuildsRowsAndFillsBars);
        TEST_FUNCTIONS.register("singlescolumngametests/standalone_bottom_is_grounded_and_floating_cell_is_rejected",
                () -> SinglesColumnGameTests::standaloneBottomIsGroundedAndFloatingCellIsRejected);
        TEST_FUNCTIONS.register("singlescolumngametests/upper_cell_uses_the_cell_immediately_below",
                () -> SinglesColumnGameTests::upperCellUsesTheCellImmediatelyBelow);
        TEST_FUNCTIONS.register("singlescolumngametests/differently_rotated_blocks_share_visual_seam_columns",
                () -> SinglesColumnGameTests::differentlyRotatedBlocksShareVisualSeamColumns);
        TEST_FUNCTIONS.register("singlescolumngametests/extraction_shifts_one_column_and_carries_item_rotation",
                () -> SinglesColumnGameTests::extractionShiftsOneColumnAndCarriesItemRotation);
        TEST_FUNCTIONS.register("singlescolumngametests/shift_keeps_an_item_it_would_no_longer_accept",
                () -> SinglesColumnGameTests::shiftKeepsAnItemItWouldNoLongerAccept);
        TEST_FUNCTIONS.register("singlescolumngametests/draw_down_keeps_an_item_it_would_no_longer_accept",
                () -> SinglesColumnGameTests::drawDownKeepsAnItemItWouldNoLongerAccept);
        TEST_FUNCTIONS.register("singlescolumngametests/extraction_preserves_an_existing_gap",
                () -> SinglesColumnGameTests::extractionPreservesAnExistingGap);
        TEST_FUNCTIONS.register("singlescolumngametests/extraction_draws_across_differently_rotated_block_boundary",
                () -> SinglesColumnGameTests::extractionDrawsAcrossDifferentlyRotatedBlockBoundary);
        TEST_FUNCTIONS.register("singlescolumngametests/capability_insertion_answers_for_the_cell_it_is_given",
                () -> SinglesColumnGameTests::capabilityInsertionAnswersForTheCellItIsGiven);
        TEST_FUNCTIONS.register("singlescolumngametests/capability_walk_fills_the_column_from_the_bottom",
                () -> SinglesColumnGameTests::capabilityWalkFillsTheColumnFromTheBottom);
        TEST_FUNCTIONS.register("singlescolumngametests/capability_extraction_uses_the_player_gravity_path",
                () -> SinglesColumnGameTests::capabilityExtractionUsesThePlayerGravityPath);
        TEST_FUNCTIONS.register("singlescolumngametests/deposit_trace_stops_at_last_empty_before_occupied_cell",
                () -> SinglesColumnGameTests::depositTraceStopsAtLastEmptyBeforeOccupiedCell);
        TEST_FUNCTIONS.register("singlescolumngametests/deposit_trace_uses_farthest_cell_when_nothing_is_occupied",
                () -> SinglesColumnGameTests::depositTraceUsesFarthestCellWhenNothingIsOccupied);
        TEST_FUNCTIONS.register("singlescolumngametests/breaking_middle_block_drops_its_contents_without_closing_the_gap",
                () -> SinglesColumnGameTests::breakingMiddleBlockDropsItsContentsWithoutClosingTheGap);
        TEST_FUNCTIONS.register("singlescolumngametests/comparator_reserves_zero_for_an_empty_column",
                () -> SinglesColumnGameTests::comparatorReservesZeroForAnEmptyColumn);
        TEST_FUNCTIONS.register("singlescolumngametests/comparator_reads_the_whole_column_from_every_block",
                () -> SinglesColumnGameTests::comparatorReadsTheWholeColumnFromEveryBlock);
        TEST_FUNCTIONS.register("singlescolumngametests/comparator_follows_a_column_losing_a_block",
                () -> SinglesColumnGameTests::comparatorFollowsAColumnLosingABlock);
        TEST_FUNCTIONS.register("stacksortgametests/comparator_orders_every_identity_component",
                () -> StackSortGameTests::comparatorOrdersEveryIdentityComponent);
        TEST_FUNCTIONS.register("storagepilegametests/deposit_fills_compatible_partial_before_empty_slot",
                () -> StoragePileGameTests::depositFillsCompatiblePartialBeforeEmptySlot);
        TEST_FUNCTIONS.register("storagepilegametests/full_pile_grows_by_one_block",
                () -> StoragePileGameTests::fullPileGrowsByOneBlock);
        TEST_FUNCTIONS.register("storagepilegametests/capability_insertion_answers_for_the_slot_it_is_given",
                () -> StoragePileGameTests::capabilityInsertionAnswersForTheSlotItIsGiven);
        TEST_FUNCTIONS.register("storagepilegametests/obstruction_prevents_growth_and_simulation_reports_no_room",
                () -> StoragePileGameTests::obstructionPreventsGrowthAndSimulationReportsNoRoom);
        TEST_FUNCTIONS.register("storagepilegametests/settle_consolidates_exact_identity_and_packs_down",
                () -> StoragePileGameTests::settleConsolidatesExactIdentityAndPacksDown);
        TEST_FUNCTIONS.register("storagepilegametests/extraction_settles_on_scheduled_block_tick",
                () -> StoragePileGameTests::extractionSettlesOnScheduledBlockTick);
        TEST_FUNCTIONS.register("storagepilegametests/empty_temporary_pile_disappears_but_permanent_pile_remains",
                () -> StoragePileGameTests::emptyTemporaryPileDisappearsButPermanentPileRemains);
        TEST_FUNCTIONS.register("storagepilegametests/capability_from_every_block_addresses_the_same_pile",
                () -> StoragePileGameTests::capabilityFromEveryBlockAddressesTheSamePile);
        TEST_FUNCTIONS.register("storagepilegametests/comparator_reads_the_whole_pile_from_every_block",
                () -> StoragePileGameTests::comparatorReadsTheWholePileFromEveryBlock);
        TEST_FUNCTIONS.register("storagepilegametests/ordinary_placement_below_permanent_pile_repairs_derived_state",
                () -> StoragePileGameTests::ordinaryPlacementBelowPermanentPileRepairsDerivedState);
        TEST_FUNCTIONS.register("storagepilegametests/breaking_middle_block_drops_its_contents_and_splits_the_pile",
                () -> StoragePileGameTests::breakingMiddleBlockDropsItsContentsAndSplitsThePile);
        TEST_FUNCTIONS.register("storagepilegametests/comparator_reserves_zero_for_an_empty_pile",
                () -> StoragePileGameTests::comparatorReservesZeroForAnEmptyPile);
        TEST_FUNCTIONS.register("storagepilegametests/comparator_contribution_loads_and_tracks_mutations",
                () -> StoragePileGameTests::comparatorContributionLoadsAndTracksMutations);
    }

    public SomeStacksGameTests(FMLJavaModLoadingContext context) {
        TEST_FUNCTIONS.register(context.getModBusGroup());
    }
}
