/** Shapes shared with the Spring Boot API. Keep in sync with the backend DTOs. */

export type BranchCode = 'AGAMY' | 'SMOUHA' | (string & {});
export type StageCode = 'CUTTING' | 'SEWING' | 'RECEIVED' | 'SOLD' | (string & {});
export type CutType = 'MAIN' | 'SECONDARY' | 'DERBY';
export type FabricUnit = 'KG' | 'LENGTH';
export type UserRole = 'OWNER' | 'ADMIN' | 'DATA_ENTRY';

export interface Branch {
  id: number;
  code: BranchCode;
  nameAr: string;
  nameEn: string | null;
  sortOrder: number;
  active: boolean;
}

export interface PipelineStage {
  id: number;
  code: StageCode;
  nameAr: string;
  nameEn: string | null;
  sequenceNo: number;
  terminal: boolean;
  active: boolean;
}

/** Error body produced by the backend's GlobalExceptionHandler. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  details: { field: string; message: string }[];
}

/** Spring Data page, serialised via DTO (spring.data.web.pageable.serialization-mode=VIA_DTO). */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export function emptyPage<T>(size = 25): Page<T> {
  return { content: [], page: { size, number: 0, totalElements: 0, totalPages: 0 } };
}

export interface FabricColor {
  id: number;
  fabricTypeId: number;
  nameAr: string;
  nameEn: string | null;
  active: boolean;
}

export interface FabricType {
  id: number;
  nameAr: string;
  nameEn: string | null;
  unit: FabricUnit;
  active: boolean;
  /** At most one derby pool per type, ever. */
  hasDerby: boolean;
  colors: FabricColor[];
}

/** One fabric type + colour pair in the inventory rollup. */

export interface FabricTypeRequest {
  nameAr: string;
  nameEn: string | null;
  unit: FabricUnit;
  active: boolean;
}

export interface FabricColorRequest {
  nameAr: string;
  nameEn: string | null;
  active: boolean;
}

export type CutStatus = 'OPEN' | 'CLOSED';

export interface BranchQuantity {
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  quantity: number;
}

/**
 * Planned quantities are derived from cut allocations — there is no stored
 * planned-quantity field. `drawsFromMultipleMainCuts` marks the rare case the
 * UI calls out so data entry does not read it as a duplicate.
 */
export interface ProductionModel {
  id: number;
  modelNumber: string;
  nameAr: string;
  nameEn: string | null;
  note: string | null;
  active: boolean;
  /** Where this model is sewn; sizes inherit it unless individually reassigned. */
  sewingBranchId: number | null;
  sewingBranchCode: BranchCode | null;
  sewingBranchNameAr: string | null;
  plannedByBranch: BranchQuantity[];
  plannedTotal: number;
  mainCutCount: number;
  drawsFromMultipleMainCuts: boolean;
}

export interface CutModelAllocation {
  id: number;
  cutId: number;
  cutNumber: string;
  cutType: CutType;
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  modelNameEn: string | null;
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  quantityAllocated: number;
  note: string | null;
}

export interface Cut {
  id: number;
  cutNumber: string;
  cutType: CutType;
  parentMainCutId: number | null;
  parentMainCutNumber: string | null;
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  fabricTypeId: number | null;
  fabricTypeNameAr: string | null;
  /** The model this cut was opened for; created with the cut when new. */
  primaryModelId: number | null;
  primaryModelNumber: string | null;
  primaryModelNameAr: string | null;
  status: CutStatus;
  cutDate: string;
  cutLength: number | null;
  modelDescription: string | null;
  labelAr: string | null;
  labelEn: string | null;
  note: string | null;
  /** Layers summed across every roll on the cut — the marker's multiplier. */
  totalLayers: number;
  totalWeightConsumed: number;
  totalDefectWeight: number;
  defectPercentage: number;
  derivedPieces: number;
  totalAllocatedPieces: number;
  weightPerPiece: number;
  modelTotals: CutModelDerived[];
  modelAllocations: CutModelAllocation[];
  sizeBreakdown: CutModelSize[];
  rolls: CutRoll[];
}

export interface ModelCuts {
  modelId: number;
  modelNumber: string;
  mainCutCount: number;
  drawsFromMultipleMainCuts: boolean;
  allocations: CutModelAllocation[];
}

export interface ModelRequest {
  modelNumber: string;
  nameAr: string;
  nameEn: string | null;
  note: string | null;
  sewingBranchId: number | null;
  active: boolean;
}

export interface CutRequest {
  cutNumber: string;
  cutType: CutType;
  parentMainCutId: number | null;
  branchId: number;
  fabricTypeId: number | null;
  /** A model number that does not exist yet is created with the cut. */
  modelNumber: string | null;
  modelNameAr: string | null;
  modelSewingBranchId: number | null;
  cutDate: string;
  cutLength: number | null;
  modelDescription: string | null;
  labelAr: string | null;
  labelEn: string | null;
  note: string | null;
}

export interface CutModelAllocationRequest {
  modelId: number;
  branchId: number;
  quantityAllocated: number;
  note: string | null;
}

export interface StageCount {
  stageId: number;
  stageCode: StageCode;
  stageNameAr: string;
  stageNameEn: string | null;
  sequenceNo: number;
  pieceCount: number;
  /** Defective pieces among pieceCount — a tag, not a separate bucket. */
  flaggedCount: number;
}

export interface BranchPipeline {
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  plannedQuantity: number;
  totalInPipeline: number;
  flaggedTotal: number;
  reconciled: boolean;
  stages: StageCount[];
}

export interface ModelPipeline {
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  modelNameEn: string | null;
  plannedTotal: number;
  totalInPipeline: number;
  flaggedTotal: number;
  reconciled: boolean;
  branches: BranchPipeline[];
}

export interface ReceiveRequest {
  modelId: number;
  branchId: number;
  quantity: number;
  receivedDate: string;
  note: string | null;
}

export interface SellRequest {
  modelId: number;
  branchId: number;
  quantity: number;
  soldDate: string;
  note: string | null;
}

export interface StageMoveRequest {
  modelId: number;
  branchId: number;
  fromStageCode: StageCode;
  toStageCode: StageCode;
  quantity: number;
  movementDate: string;
  note: string | null;
}

export interface FlagRequest {
  modelId: number;
  branchId: number;
  stageCode: StageCode | null;
  quantity: number;
  reason: string | null;
  eventDate: string;
}

export interface StageTotal {
  stageId: number;
  stageCode: StageCode;
  stageNameAr: string;
  stageNameEn: string | null;
  sequenceNo: number;
  pieceCount: number;
  flaggedCount: number;
}

export interface BranchRollup {
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  plannedTotal: number;
  totalInPipeline: number;
  flaggedTotal: number;
  reconciled: boolean;
  modelCount: number;
  stages: StageTotal[];
}

export interface Overview {
  plannedTotal: number;
  totalInPipeline: number;
  flaggedTotal: number;
  reconciled: boolean;
  modelCount: number;
  stages: StageTotal[];
  branches: BranchRollup[];
}

export interface FlaggedRow {
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  modelNameEn: string | null;
  branchId: number;
  branchCode: BranchCode;
  branchNameAr: string;
  branchNameEn: string | null;
  stageId: number;
  stageCode: StageCode;
  flaggedCount: number;
  pieceCount: number;
}

export interface UserRequest {
  username: string;
  password: string | null;
  displayName: string;
  role: UserRole;
  enabled: boolean;
}

/** Which of a fabric type's two stock pools something belongs to. */
export type FabricPool = 'REGULAR' | 'DERBY';

export interface Derby {
  id: number;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  note: string | null;
}

export interface FabricIntakeColorRow {
  id: number;
  colorId: number;
  colorNameAr: string;
  colorNameEn: string | null;
  rollCount: number;
  /** Optional — the breakdown is valid without it. */
  quantity: number | null;
}

/**
 * One dated purchase. The colour breakdown is soft: exactly one of
 * `unassignedRolls` / `overAssignedRolls` may be non-zero, and both are advisory.
 */
export interface FabricIntake {
  id: number;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  fabricTypeNameEn: string | null;
  unit: FabricUnit;
  pool: FabricPool;
  supplierId: number | null;
  supplierNameAr: string | null;
  intakeDate: string;
  totalRolls: number;
  consumedRolls: number;
  remainingRolls: number;
  totalQuantity: number;
  consumedQuantity: number;
  remainingQuantity: number;
  /** Null for anyone but the owner, and null until a price is recorded. */
  pricePerUnit: number | null;
  totalCost: number | null;
  assignedRolls: number;
  unassignedRolls: number;
  overAssignedRolls: number;
  note: string | null;
  colorBreakdown: FabricIntakeColorRow[];
}

/** Indicative: consumption can happen before a batch's colours are known. */
export interface ColorStock {
  colorId: number;
  colorNameAr: string;
  colorNameEn: string | null;
  assignedRolls: number;
  assignedQuantity: number;
  consumedRolls: number;
  consumedQuantity: number;
}

export interface FabricStock {
  fabricTypeId: number;
  fabricTypeNameAr: string;
  fabricTypeNameEn: string | null;
  unit: FabricUnit;
  pool: FabricPool;
  batchCount: number;
  totalRolls: number;
  remainingRolls: number;
  totalQuantity: number;
  remainingQuantity: number;
  /** Null for anyone but the owner. */
  totalCost: number | null;
  colors: ColorStock[];
  unassignedRolls: number;
}

export interface IntakeRemainingRow {
  intakeId: number;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  fabricTypeNameEn: string | null;
  unit: FabricUnit;
  pool: FabricPool;
  intakeDate: string;
  totalRolls: number;
  remainingRolls: number;
  totalQuantity: number;
  remainingQuantity: number;
  /** Null for anyone but the owner. */
  pricePerUnit: number | null;
}

export interface FabricIntakeRequest {
  fabricTypeId: number;
  derbyPool: boolean;
  supplierId: number | null;
  intakeDate: string;
  totalRolls: number;
  totalQuantity: number;
  /** Optional — the owner fills it in later. */
  pricePerUnit: number | null;
  note: string | null;
}

export interface FabricIntakeColorRequest {
  fabricColorId: number;
  rollCount: number;
  quantity: number | null;
}

export interface DerbyRequest {
  note: string | null;
}

export interface SizeCategory {
  id: number;
  code: string;
  nameAr: string;
  nameEn: string | null;
  note: string | null;
  sortOrder: number;
  active: boolean;
  sizes: GarmentSize[];
}

export interface GarmentSize {
  id: number;
  categoryId: number;
  categoryNameAr: string;
  code: string;
  nameAr: string | null;
  sortOrder: number;
  active: boolean;
}

export interface ModelSizeCategoryRow {
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  categoryId: number;
  categoryNameAr: string;
  piecesPerLayer: number;
}

/** One roll's use by one cut. `done` is what closes the roll and moves the count. */
export interface CutRoll {
  id: number;
  cutId: number;
  fabricRollId: number;
  rollLabel: string | null;
  intakeDate: string;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  unit: FabricUnit;
  fabricColorId: number | null;
  colorNameAr: string | null;
  layers: number;
  weightAtStart: number;
  weightConsumed: number;
  remainingAfter: number;
  defectWeight: number;
  done: boolean;
  rollClosed: boolean;
  note: string | null;
}

export interface CutModelSize {
  id: number;
  modelId: number;
  modelNumber: string;
  sizeId: number;
  sizeCode: string;
  categoryId: number;
  categoryNameAr: string;
  piecesPerLayer: number;
  totalPieces: number;
  /** Where this size is sewn, after inheriting from the model. */
  branchId: number | null;
  branchCode: BranchCode | null;
  branchNameAr: string | null;
  /** True when the size names its own branch rather than inheriting. */
  branchOverridden: boolean;
}

/** What the marker says a model yields, against what is allocated to branches. */
export interface CutModelDerived {
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  piecesPerLayer: number;
  derivedPieces: number;
  allocatedPieces: number;
  unallocatedPieces: number;
  balanced: boolean;
}

export interface FabricRoll {
  id: number;
  fabricIntakeId: number;
  intakeDate: string;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  unit: FabricUnit;
  pool: FabricPool;
  fabricColorId: number | null;
  colorNameAr: string | null;
  label: string | null;
  initialWeight: number;
  remainingWeight: number;
  consumedWeight: number;
  closed: boolean;
}

export interface OpenRollRow {
  fabricTypeId: number;
  fabricTypeNameAr: string;
  colorId: number | null;
  colorNameAr: string | null;
  openRolls: number;
  remainingWeight: number;
}

export interface ColorRollCount {
  colorId: number | null;
  colorNameAr: string | null;
  rollCount: number;
  remainingWeight: number;
}

export interface CutRollRequest {
  fabricRollId: number | null;
  fabricIntakeId: number | null;
  fabricColorId: number | null;
  rollLabel: string | null;
  initialWeight: number | null;
  layers: number;
  defectWeight: number | null;
  done: boolean;
  /** What this cut used. Ignored when done; the remainder is calculated. */
  weightUsed: number | null;
  note: string | null;
}

export interface CutModelSizeRequest {
  /**
   * Identify the model by id or number. Leave both unset to use the cut's own
   * model, which is the normal case.
   */
  modelId: number | null;
  modelNumber: string | null;
  modelNameAr: string | null;
  garmentSizeId: number;
  piecesPerLayer: number;
  /** Null inherits the model's sewing branch. */
  branchId: number | null;
}

export interface Supplier {
  id: number;
  nameAr: string;
  nameEn: string | null;
  phone: string | null;
  note: string | null;
  active: boolean;
}

export interface SupplierRequest {
  nameAr: string;
  nameEn: string | null;
  phone: string | null;
  note: string | null;
  active: boolean;
}

/**
 * What a fabric has cost. `averagePrice` is weighted by quantity — total spent
 * over total bought — not a plain mean of batch prices.
 * `supplierId` is null on the unsplit view.
 */
export interface FabricPriceRow {
  fabricTypeId: number;
  fabricTypeNameAr: string;
  fabricTypeNameEn: string | null;
  unit: FabricUnit;
  supplierId: number | null;
  supplierNameAr: string | null;
  batchCount: number;
  totalQuantity: number;
  totalCost: number;
  averagePrice: number;
  minPrice: number;
  maxPrice: number;
  latestPrice: number | null;
  latestDate: string | null;
}

export type RemainingGrouping = 'TOTAL' | 'DATE' | 'SUPPLIER';

/**
 * Remaining stock at one of three levels. `intakeDate` and the supplier fields
 * are populated only by the grouping that asks for them.
 */
export interface RemainingRow {
  fabricTypeId: number;
  fabricTypeNameAr: string;
  fabricTypeNameEn: string | null;
  unit: FabricUnit;
  intakeDate: string | null;
  supplierId: number | null;
  supplierNameAr: string | null;
  batchCount: number;
  totalRolls: number;
  remainingRolls: number;
  totalQuantity: number;
  remainingQuantity: number;
}

/**
 * Fabric consumed per piece of a model. A cut making several models has its
 * fabric split between them in proportion to the pieces each takes.
 */
export interface ModelFabricUsage {
  modelId: number;
  modelNumber: string;
  modelNameAr: string;
  fabricTypeId: number;
  fabricTypeNameAr: string;
  unit: FabricUnit;
  /** Which run the fabric went into: the body, or a secondary/derby addition. */
  cutType: CutType;
  cutCount: number;
  totalPieces: number;
  totalWeight: number;
  weightPerPiece: number;
}
