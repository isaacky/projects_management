import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload } from './client.js';

const BASE = '/api/v1/procurement-plans';

export const planKeys = {
  all: ['procurement-plans'],
  list: ['procurement-plans', 'list'],
  detail: (id) => ['procurement-plans', id],
  available: (projectId) => ['procurement-plans', 'available-lines', projectId ?? null],
};

export function useProcurementPlans() {
  return useQuery({
    queryKey: planKeys.list,
    queryFn: ({ signal }) => apiFetch(BASE, { signal }),
  });
}

export function useProcurementPlan(id) {
  return useQuery({
    queryKey: planKeys.detail(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

/** Approved lines of the current year's plan that a requisition can draw on. */
export function useAvailablePlanLines(projectId) {
  const qs = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
  return useQuery({
    queryKey: planKeys.available(projectId),
    queryFn: ({ signal }) => apiFetch(`${BASE}/available-lines${qs}`, { signal }),
    staleTime: 60_000,
  });
}

export function useCreatePlan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch(BASE, { method: 'POST', body }),
    onSuccess: (plan) => {
      qc.setQueryData(planKeys.detail(plan.id), plan);
      qc.invalidateQueries({ queryKey: planKeys.list });
    },
  });
}

/**
 * Any change to a plan; every endpoint returns the updated plan.
 * vars: { path: '' | '/submit' | '/lines/<id>' ..., method: 'POST' | 'PUT' | 'DELETE', body }
 */
export function usePlanAction(planId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ path, method = 'POST', body }) =>
      apiFetch(`${BASE}/${planId}${path}`, { method, body: method === 'DELETE' ? undefined : (body ?? {}) }),
    onSuccess: (plan) => {
      if (plan?.id) qc.setQueryData(planKeys.detail(plan.id), plan);
      qc.invalidateQueries({ queryKey: planKeys.list });
      qc.invalidateQueries({ queryKey: ['procurement-plans', 'available-lines'] });
      qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

/** Uploads a CSV of lines for a department. Resolves to { imported, errors, plan }. */
export function useImportPlanLines(planId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ departmentId, file }) => {
      const form = new FormData();
      form.append('file', file);
      return apiUpload(`${BASE}/${planId}/departments/${departmentId}/import`, form);
    },
    onSuccess: (result) => {
      if (result?.plan?.id) qc.setQueryData(planKeys.detail(result.plan.id), result.plan);
      qc.invalidateQueries({ queryKey: planKeys.list });
    },
  });
}
