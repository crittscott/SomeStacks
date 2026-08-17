All nine targeted reviews are complete. Reports are in the project root:

code-review-dead-code.md — no unused code, but BarColumnHandler/SinglesColumnHandler (Forge) are near-total duplicates of each other; Fabric already solved the same problem non-duplicatively in FabricRunItemStorage.



code-review-protection.md — Fabric has no claim-mod veto for automated block growth (only removal), while Forge covers both; an acknowledged Fabric API gap, not an oversight, but an asymmetry worth knowing about.






Net picture: the codebase held up well across guarding, reinvention, networking, and legacy-support — those came back essentially clean. The recurring real theme is the Bar/Singles/Storage triad's parallel evolution (organization, dead-code, magic-numbers all point at it from different angles) plus one Fabric-specific protection gap and a Fabric-specific javadoc gap. Let me know if you want me to dig into any one of these further or start addressing a specific report.