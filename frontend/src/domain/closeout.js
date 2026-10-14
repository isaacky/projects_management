// Closeout and handover: labels and small calculations.

export const CLOSEOUT_STATUS = {
  OPEN: { label: 'In progress', color: 'info' },
  HANDOVER_SUBMITTED: { label: 'Handover awaiting acceptance', color: 'warning' },
  HANDED_OVER: { label: 'Handed over', color: 'success' },
  CLOSED: { label: 'Closed', color: 'default' },
};

export const ITEM_STATUS = {
  OPEN: { label: 'Open', color: 'default' },
  DONE: { label: 'Done', color: 'success' },
  NOT_APPLICABLE: { label: 'Not applicable', color: 'default' },
};

export const DEFECT_SEVERITY = {
  MINOR: { label: 'Minor', color: 'default' },
  MAJOR: { label: 'Major', color: 'warning' },
  CRITICAL: { label: 'Critical', color: 'error' },
};

export const DEFECT_STATUS = {
  OPEN: { label: 'Open', color: 'error' },
  RECTIFIED: { label: 'Put right, to check', color: 'warning' },
  CLOSED: { label: 'Closed', color: 'success' },
  WAIVED: { label: 'Waived', color: 'default' },
};

export const LESSON_KIND = {
  WENT_WELL: { label: 'Went well', color: 'success' },
  IMPROVE: { label: 'To improve', color: 'warning' },
};

export const LESSON_CATEGORY = {
  PROCUREMENT: 'Procurement',
  DESIGN: 'Design',
  SCHEDULE: 'Schedule',
  COST: 'Cost',
  CONTRACTOR: 'Contractor',
  STAKEHOLDERS: 'Stakeholders',
  WAYLEAVE: 'Wayleave',
  SAFETY: 'Safety',
  TECHNICAL: 'Technical',
  OTHER: 'Other',
};

export const LESSON_STATUS = {
  SUBMITTED: { label: 'Awaiting PMO review', color: 'default' },
  ADOPTED: { label: 'Adopted', color: 'success' },
  NOTED: { label: 'Noted', color: 'info' },
};

/** Done and not-applicable items both count as finished. */
export function checklistProgress(items) {
  const done = items.filter((i) => i.status === 'DONE').length;
  const na = items.filter((i) => i.status === 'NOT_APPLICABLE').length;
  const finished = done + na;
  return { done, na, open: items.length - finished, total: items.length, pct: items.length ? Math.round((finished * 100) / items.length) : 0 };
}

export function defectCounts(defects) {
  const outstanding = defects.filter((d) => d.status === 'OPEN' || d.status === 'RECTIFIED');
  return {
    outstanding: outstanding.length,
    critical: outstanding.filter((d) => d.severity === 'CRITICAL').length,
    overdue: defects.filter((d) => d.overdue).length,
    toCheck: defects.filter((d) => d.status === 'RECTIFIED').length,
  };
}

/** Rows of the handover asset editor -> request body; invalid rows give errors keyed by row index. */
export function assetsToRequest(rows, toMinor) {
  const errors = {};
  const assets = rows.map((r, i) => {
    const quantity = Number(r.quantity);
    const valueMinor = String(r.value ?? '').trim() === '' ? null : toMinor(r.value);
    if (!r.description.trim()) errors[i] = 'Describe the asset';
    else if (!(quantity > 0)) errors[i] = 'Quantity must be more than zero';
    else if (!r.unit.trim()) errors[i] = 'Give the unit';
    else if (Number.isNaN(valueMinor)) errors[i] = 'Value must be an amount, e.g. 1500000';
    return {
      description: r.description.trim(),
      quantity,
      unit: r.unit.trim(),
      tag: r.tag?.trim() || null,
      location: r.location?.trim() || null,
      valueMinor,
    };
  });
  return { assets, errors };
}
