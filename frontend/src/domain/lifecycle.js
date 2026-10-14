// Mirrors com.pmplatform.portfolio.ProjectPhase on the backend.
export const PHASES = [
  { key: 'INITIATION', label: 'Initiation', gate: 'G0', gateName: 'Approval to procure' },
  { key: 'PROCUREMENT', label: 'Procurement', gate: 'G1', gateName: 'Contract award approved' },
  { key: 'PLANNING', label: 'Planning', gate: 'G2', gateName: 'Baseline approved' },
  { key: 'EXECUTION', label: 'Execution', gate: 'G3', gateName: 'Practical completion' },
  { key: 'CLOSEOUT', label: 'Closeout', gate: 'G4', gateName: 'Formal closure' },
  { key: 'IMPLEMENTATION', label: 'Implementation', gate: null, gateName: null },
];

export const PHASE_COLORS = {
  INITIATION: 'default',
  PROCUREMENT: 'secondary',
  PLANNING: 'info',
  EXECUTION: 'primary',
  CLOSEOUT: 'warning',
  IMPLEMENTATION: 'success',
};

export function phaseLabel(key) {
  return PHASES.find((p) => p.key === key)?.label ?? key;
}

export function phaseIndex(key) {
  return PHASES.findIndex((p) => p.key === key);
}
