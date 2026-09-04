package com.apparel.tracking.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.apparel.tracking.auth.domain.UserRole;
import com.apparel.tracking.auth.dto.UserRequest;
import com.apparel.tracking.auth.repository.AppUserRepository;
import com.apparel.tracking.auth.service.UserService;
import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.fabric.dto.FabricColorRequest;
import com.apparel.tracking.fabric.dto.FabricIntakeColorRequest;
import com.apparel.tracking.fabric.dto.FabricIntakeDto;
import com.apparel.tracking.fabric.dto.FabricIntakeRequest;
import com.apparel.tracking.fabric.dto.FabricTypeRequest;
import com.apparel.tracking.fabric.service.DerbyService;
import com.apparel.tracking.fabric.service.FabricCatalogService;
import com.apparel.tracking.fabric.service.FabricIntakeService;
import com.apparel.tracking.pipeline.dto.BranchPipelineDto;
import com.apparel.tracking.pipeline.dto.FlagRequest;
import com.apparel.tracking.pipeline.dto.ModelPipelineDto;
import com.apparel.tracking.pipeline.dto.StageCountDto;
import com.apparel.tracking.pipeline.dto.ReceiveRequest;
import com.apparel.tracking.pipeline.dto.SellRequest;
import com.apparel.tracking.pipeline.dto.StageMoveRequest;
import com.apparel.tracking.pipeline.service.PipelineService;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutDto;
import com.apparel.tracking.production.dto.CutModelAllocationRequest;
import com.apparel.tracking.production.dto.CutModelDerivedDto;
import com.apparel.tracking.production.dto.CutRequest;
import com.apparel.tracking.production.dto.CutModelSizeRequest;
import com.apparel.tracking.production.dto.CutRollRequest;
import com.apparel.tracking.production.dto.ModelRequest;
import com.apparel.tracking.production.repository.ModelRepository;
import com.apparel.tracking.production.service.CutService;
import com.apparel.tracking.production.service.ModelService;
import com.apparel.tracking.reference.repository.BranchRepository;
import com.apparel.tracking.size.repository.GarmentSizeRepository;
import com.apparel.tracking.supplier.dto.SupplierRequest;
import com.apparel.tracking.supplier.service.SupplierService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browsable demo data, so a fresh database has something to look at.
 *
 * <p>Off by default; enable with {@code app.seed.demo-data=true} (or
 * {@code APP_SEED_DEMO_DATA=true}). Skipped when any model already exists, so it
 * never doubles up on a database that has been used.
 *
 * <p>Everything goes through the normal services rather than raw SQL: the demo
 * data is therefore subject to the same invariants as real data, and seeding
 * doubles as a smoke test of the whole write path.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final FabricCatalogService catalog;
    private final FabricIntakeService intakes;
    private final DerbyService derbies;
    private final ModelService models;
    private final CutService cuts;
    private final PipelineService pipeline;
    private final UserService users;
    private final ModelRepository modelRepository;
    private final AppUserRepository userRepository;
    private final BranchRepository branches;
    private final GarmentSizeRepository sizes;
    private final SupplierService suppliers;

    public DemoDataSeeder(
            FabricCatalogService catalog,
            FabricIntakeService intakes,
            DerbyService derbies,
            ModelService models,
            CutService cuts,
            PipelineService pipeline,
            UserService users,
            ModelRepository modelRepository,
            AppUserRepository userRepository,
            BranchRepository branches,
            GarmentSizeRepository sizes,
            SupplierService suppliers) {
        this.catalog = catalog;
        this.intakes = intakes;
        this.derbies = derbies;
        this.models = models;
        this.cuts = cuts;
        this.pipeline = pipeline;
        this.users = users;
        this.modelRepository = modelRepository;
        this.userRepository = userRepository;
        this.branches = branches;
        this.sizes = sizes;
        this.suppliers = suppliers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (modelRepository.count() > 0) {
            log.info("Demo data skipped: the database already has models.");
            return;
        }

        LocalDate today = LocalDate.now();
        Long agamy = branchId("AGAMY");
        Long smouha = branchId("SMOUHA");

        seedUsers();
        Map<String, Long> supplierIds = seedSuppliers();
        FabricSeed fabric = seedFabric(today, supplierIds);
        Map<String, Long> modelIds = seedModels(agamy, smouha);
        Map<String, Long> cutIds = seedCuts(today, agamy, smouha, fabric.typeIdsByName());

        allocateFabric(cutIds, fabric);
        seedMarkers(cutIds, modelIds, smouha);
        progressPipeline(modelIds, agamy, smouha, today);

        log.info("Demo data seeded: {} models, {} cuts.", modelIds.size(), cutIds.size());
    }

    /**
     * Suppliers. Cotton is bought from two of them on purpose, so the
     * average-price-per-supplier report has something real to compare.
     */
    private Map<String, Long> seedSuppliers() {
        Map<String, Long> ids = new LinkedHashMap<>();
        record SupplierSpec(String nameAr, String nameEn, String phone) {}

        for (SupplierSpec spec : List.of(
                new SupplierSpec("مصانع الدلتا للغزل", "Delta Spinning", "01001234567"),
                new SupplierSpec("شركة النيل للأقمشة", "Nile Textiles", "01112345678"),
                new SupplierSpec("مستورد الإسكندرية", "Alexandria Importer", "01223456789"))) {
            ids.put(spec.nameAr(), suppliers.create(
                    new SupplierRequest(spec.nameAr(), spec.nameEn(), spec.phone(), null, true)).id());
        }
        return ids;
    }

    private void seedUsers() {
        if (!userRepository.existsByUsernameIgnoreCase("entry")) {
            users.create(new UserRequest("entry", "entry12345", "منى — إدخال بيانات", UserRole.DATA_ENTRY, true));
        }
        // A manager: full operational control, but no sight of what fabric costs.
        if (!userRepository.existsByUsernameIgnoreCase("manager")) {
            users.create(new UserRequest("manager", "manager12345", "سامي — مدير", UserRole.ADMIN, true));
        }
    }

    /** What the fabric step produced, for the allocation step to draw on. */
    private record FabricSeed(
            Map<String, Long> colorsByName,
            Map<String, Long> typeIdsByName,
            Map<String, Long> regularIntakeByType,
            Map<String, Long> derbyIntakeByType) {
    }

    /**
     * Fabric arrives as dated purchases: a roll count, a total weight and the price
     * paid that day, with the colour breakdown filled in afterwards — deliberately
     * leaving some rolls unassigned, which is the normal state.
     */
    private FabricSeed seedFabric(LocalDate today, Map<String, Long> supplierIds) {
        List<Long> supplierPool = List.copyOf(supplierIds.values());
        record ColourSpec(String nameAr, String nameEn) {}
        record TypeSpec(String nameAr, String nameEn, FabricUnit unit, boolean withDerby, List<ColourSpec> colors) {}

        List<TypeSpec> specs = List.of(
                new TypeSpec("قطن", "Cotton", FabricUnit.KG, true, List.of(
                        new ColourSpec("أبيض", "White"),
                        new ColourSpec("أسود", "Black"),
                        new ColourSpec("كحلي", "Navy"))),
                new TypeSpec("جينز", "Denim", FabricUnit.KG, true, List.of(
                        new ColourSpec("أزرق فاتح", "Light blue"),
                        new ColourSpec("أزرق غامق", "Indigo"))),
                new TypeSpec("بوليستر", "Polyester", FabricUnit.LENGTH, false, List.of(
                        new ColourSpec("بيج", "Beige"),
                        new ColourSpec("رمادي", "Grey"))),
                new TypeSpec("كتان", "Linen", FabricUnit.LENGTH, false, List.of(
                        new ColourSpec("أوف وايت", "Off white"))));

        Map<String, Long> colorsByName = new LinkedHashMap<>();
        Map<String, Long> typeIdsByName = new LinkedHashMap<>();
        Map<String, Long> regularIntakeByType = new LinkedHashMap<>();
        Map<String, Long> derbyIntakeByType = new LinkedHashMap<>();

        int n = 0;
        for (TypeSpec spec : specs) {
            Long typeId = catalog.createType(
                    new FabricTypeRequest(spec.nameAr(), spec.nameEn(), spec.unit(), true)).id();
            typeIdsByName.put(spec.nameAr(), typeId);

            List<Long> colorIds = new java.util.ArrayList<>();
            for (ColourSpec colour : spec.colors()) {
                Long colorId = catalog
                        .addColor(typeId, new FabricColorRequest(colour.nameAr(), colour.nameEn(), true))
                        .id();
                colorsByName.put(colour.nameAr(), colorId);
                colorIds.add(colorId);
            }

            if (spec.withDerby()) {
                derbies.create(typeId, new DerbyService.DerbyRequest("دربي " + spec.nameAr()));
            }

            // Two purchases of regular stock on different dates, at different prices.
            int rolls = 120 + (n * 40);
            // Two purchases from *different* suppliers at different prices, so the
            // per-supplier comparison is meaningful rather than degenerate.
            Long firstSupplier = supplierPool.get(n % supplierPool.size());
            Long secondSupplier = supplierPool.get((n + 1) % supplierPool.size());

            FabricIntakeDto first = intakes.create(new FabricIntakeRequest(
                    typeId, false, firstSupplier, today.minusDays(60 - n * 4L), rolls,
                    BigDecimal.valueOf(rolls * 9L), BigDecimal.valueOf(42.5 + n * 3), null));
            intakes.create(new FabricIntakeRequest(
                    typeId, false, secondSupplier, today.minusDays(21 - n * 2L), 60 + n * 10,
                    BigDecimal.valueOf((60 + n * 10) * 9L), BigDecimal.valueOf(46.0 + n * 3), "دفعة ثانية"));
            regularIntakeByType.put(spec.nameAr(), first.id());

            // Colour breakdown on the first batch, deliberately short of the total.
            int assigned = 0;
            for (int i = 0; i < colorIds.size() && assigned < rolls - 20; i++) {
                int count = 30 + i * 10;
                intakes.setColorBreakdown(first.id(), new FabricIntakeColorRequest(
                        colorIds.get(i), count,
                        i == 0 ? BigDecimal.valueOf(count * 9L) : null));
                assigned += count;
            }

            if (spec.withDerby()) {
                FabricIntakeDto derbyBatch = intakes.create(new FabricIntakeRequest(
                        typeId, true, firstSupplier, today.minusDays(35 - n * 3L), 25 + n * 5,
                        BigDecimal.valueOf((25 + n * 5) * 8L), BigDecimal.valueOf(48.0 + n * 2), "دربي"));
                intakes.setColorBreakdown(derbyBatch.id(),
                        new FabricIntakeColorRequest(colorIds.get(0), 10, null));
                derbyIntakeByType.put(spec.nameAr(), derbyBatch.id());
            }
            n++;
        }

        return new FabricSeed(colorsByName, typeIdsByName, regularIntakeByType, derbyIntakeByType);
    }

    private Map<String, Long> seedModels(Long agamy, Long smouha) {
        Map<String, Long> ids = new LinkedHashMap<>();
        record ModelSpec(String number, String nameAr, String nameEn, Long sewingBranch) {}

        List<ModelSpec> specs = List.of(
                new ModelSpec("200", "قميص كلاسيك", "Classic shirt", agamy),
                new ModelSpec("310", "تي شيرت قطن", "Cotton t-shirt", agamy),
                new ModelSpec("500", "بنطلون جينز", "Jeans", smouha),
                new ModelSpec("620", "جاكيت كتان", "Linen jacket", agamy));

        for (ModelSpec spec : specs) {
            ids.put(spec.number(), models.create(new ModelRequest(
                    spec.number(), spec.nameAr(), spec.nameEn(), null, spec.sewingBranch(), true)).id());
        }
        return ids;
    }

    private Map<String, Long> seedCuts(
            LocalDate today, Long agamy, Long smouha, Map<String, Long> fabricTypeIds) {
        Long cottonType = fabricTypeIds.get("قطن");
        Long denimType = fabricTypeIds.get("جينز");
        Long linenType = fabricTypeIds.get("كتان");

        Map<String, Long> ids = new LinkedHashMap<>();

        ids.put("CUT-1", cuts.create(new CutRequest(
                "CUT-1", CutType.MAIN, null, agamy, cottonType, "200", "قميص كلاسيك", agamy, today.minusDays(45),
                BigDecimal.valueOf(6.5), "قميص كلاسيك وتي شيرت", "تقطيعة القمصان", "Shirt run", null)).id());
        ids.put("CUT-2", cuts.create(new CutRequest(
                "CUT-2", CutType.MAIN, null, smouha, denimType, "500", "بنطلون جينز", smouha, today.minusDays(30),
                BigDecimal.valueOf(7.25), "بنطلون جينز", "تقطيعة الجينز", "Denim run", null)).id());
        ids.put("CUT-3", cuts.create(new CutRequest(
                "CUT-3", CutType.MAIN, null, agamy, linenType, "620", "جاكيت كتان", agamy, today.minusDays(12),
                BigDecimal.valueOf(5.0), "جاكيت كتان", "تقطيعة الكتان", "Linen run", null)).id());

        // Secondary and derby cuts hang off a main cut.
        ids.put("CUT-1S", cuts.create(new CutRequest(
                "CUT-1S", CutType.SECONDARY, ids.get("CUT-1"), agamy, cottonType, "310", "تي شيرت قطن", agamy, today.minusDays(40),
                BigDecimal.valueOf(2.0), "أكمام إضافية", "أكمام إضافية", "Extra sleeves", null)).id());
        ids.put("CUT-2D", cuts.create(new CutRequest(
                "CUT-2D", CutType.DERBY, ids.get("CUT-2"), smouha, denimType, "500", "بنطلون جينز", smouha, today.minusDays(25),
                BigDecimal.valueOf(3.5), "دربي الجينز", "دربي الجينز", "Denim derby", null)).id());

        return ids;
    }

    /**
     * Puts rolls on the cuts, exercising both halves of the consumption
     * lifecycle: rolls finished outright, and rolls left part-used for a later
     * cut to pick up. The derby cut draws derby stock — the seed would fail
     * loudly if that rule were wrong.
     */
    private void allocateFabric(Map<String, Long> cutIds, FabricSeed fabric) {
        record Draw(String cut, String fabricType, String colour, double weight, int layers,
                    double defect, boolean done, double remaining, boolean derby) {}

        List<Draw> draws = List.of(
                new Draw("CUT-1", "قطن", "أبيض", 9.5, 40, 0.4, true, 0, false),
                // Left open on purpose: 2.5 kg stays on this roll.
                new Draw("CUT-1", "قطن", "كحلي", 10.0, 30, 0.3, false, 2.5, false),
                new Draw("CUT-1S", "قطن", "أبيض", 8.0, 12, 0.2, true, 0, false),
                new Draw("CUT-2", "جينز", "أزرق غامق", 11.0, 45, 0.6, true, 0, false),
                new Draw("CUT-3", "كتان", "أوف وايت", 9.0, 25, 0.35, true, 0, false),
                new Draw("CUT-2D", "جينز", "أزرق فاتح", 7.5, 15, 0.25, true, 0, true));

        for (Draw draw : draws) {
            Long intakeId = draw.derby()
                    ? fabric.derbyIntakeByType().get(draw.fabricType())
                    : fabric.regularIntakeByType().get(draw.fabricType());
            if (intakeId == null) {
                continue;
            }
            cuts.addRoll(cutIds.get(draw.cut()), new CutRollRequest(
                    null,
                    intakeId,
                    fabric.colorsByName().get(draw.colour()),
                    null,
                    BigDecimal.valueOf(draw.weight()),
                    draw.layers(),
                    BigDecimal.valueOf(draw.defect()),
                    draw.done(),
                    BigDecimal.valueOf(draw.remaining()),
                    null));
        }
    }

    /**
     * The marker: pieces per layer per size. Two models on one cut using entirely
     * different size sets, which is the case the spec calls out.
     */
    private void seedMarkers(Map<String, Long> cutIds, Map<String, Long> modelIds, Long smouha) {
        // branch == null means the size inherits the model's own sewing branch.
        record Marker(String cut, String model, String sizeCode, int perLayer, Long branch) {}

        List<Marker> markers = List.of(
                // Model 200 is split: the smaller sizes stay at Agamy, 16 goes to Smouha.
                new Marker("CUT-1", "200", "12", 5, null),
                new Marker("CUT-1", "200", "14", 4, null),
                new Marker("CUT-1", "200", "16", 3, smouha),
                new Marker("CUT-1", "310", "6", 4, null),
                new Marker("CUT-1", "310", "8", 3, null),
                new Marker("CUT-1", "310", "10", 2, null),
                new Marker("CUT-2", "500", "L", 6, null),
                new Marker("CUT-2", "500", "XL", 4, null),
                new Marker("CUT-3", "620", "XL", 3, null),
                new Marker("CUT-3", "620", "XXL", 2, null),
                // Model 500 also comes off a second main cut — rare, but real.
                new Marker("CUT-3", "500", "L", 2, null),
                new Marker("CUT-1S", "310", "6", 2, null),
                new Marker("CUT-2D", "500", "L", 1, null));

        for (Marker marker : markers) {
            sizes.findByCodeIgnoreCase(marker.sizeCode()).ifPresent(size ->
                    cuts.setModelSize(cutIds.get(marker.cut()), new CutModelSizeRequest(
                            modelIds.get(marker.model()), null, null,
                            size.getId(), marker.perLayer(), marker.branch())));
        }
    }

    /**
     * Walks pieces partway down the pipeline so every stage has something in it.
     *
     * <p>Proportional rather than absolute: the quantities now come from the
     * markers, so fixed numbers would go stale the moment a marker changed.
     */
    private void progressPipeline(Map<String, Long> modelIds, Long agamy, Long smouha, LocalDate today) {
        for (Long modelId : modelIds.values()) {
            ModelPipelineDto model = pipeline.pipelineForModel(modelId);

            for (BranchPipelineDto branch : model.branches()) {
                int cutting = stagePieces(branch, PipelineService.STAGE_CUTTING);
                if (cutting == 0) {
                    continue;
                }

                // Leave one branch of one model entirely at cutting, so the
                // dashboard shows that state too.
                if (branch.branchId().equals(smouha) && model.modelNumber().equals("620")) {
                    continue;
                }

                int toSewing = (int) Math.round(cutting * 0.75);
                if (toSewing == 0) {
                    continue;
                }
                move(modelId, branch.branchId(), PipelineService.STAGE_CUTTING,
                        "SEWING", toSewing, today.minusDays(20));

                int toReceive = (int) Math.round(toSewing * 0.6);
                if (toReceive == 0) {
                    continue;
                }
                pipeline.receive(new ReceiveRequest(
                        modelId, branch.branchId(), toReceive, today.minusDays(9), null));

                // Defects are found at receiving inspection.
                int flagged = Math.max(1, (int) Math.round(toReceive * 0.05));
                pipeline.flag(new FlagRequest(
                        modelId, branch.branchId(), null, flagged, "عيب في الخياطة", today.minusDays(8)));

                int sellable = toReceive - flagged;
                int toSell = (int) Math.round(sellable * 0.6);
                if (toSell > 0) {
                    pipeline.sell(new SellRequest(
                            modelId, branch.branchId(), toSell, today.minusDays(3), null));
                }
            }
        }
    }

    private int stagePieces(BranchPipelineDto branch, String stageCode) {
        return branch.stages().stream()
                .filter(stage -> stage.stageCode().equals(stageCode))
                .mapToInt(StageCountDto::pieceCount)
                .findFirst()
                .orElse(0);
    }

    private void move(Long modelId, Long branchId, String from, String to, int quantity, LocalDate date) {
        pipeline.move(new StageMoveRequest(modelId, branchId, from, to, quantity, date, null));
    }

    private Long branchId(String code) {
        return branches.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Branch " + code + " is missing"))
                .getId();
    }
}
