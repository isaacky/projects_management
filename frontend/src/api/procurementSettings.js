import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

const BASE = '/api/v1/procurement/settings';
const KEY = ['procurement-settings'];

export function useProcurementSettings() {
  return useQuery({ queryKey: KEY, queryFn: ({ signal }) => apiFetch(BASE, { signal }) });
}

function useSettingsMutation(fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export const useSaveMethod = () =>
  useSettingsMutation(({ code, ...body }) =>
    apiFetch(`${BASE}/methods/${encodeURIComponent(code)}`, { method: 'PUT', body }),
  );

export const useSaveThreshold = () =>
  useSettingsMutation(({ id, ...body }) =>
    id
      ? apiFetch(`${BASE}/approval-thresholds/${id}`, { method: 'PUT', body })
      : apiFetch(`${BASE}/approval-thresholds`, { method: 'POST', body }),
  );

export const useDeleteThreshold = () =>
  useSettingsMutation((id) => apiFetch(`${BASE}/approval-thresholds/${id}`, { method: 'DELETE' }));

/** methodCode null = the default rule. */
export const useSaveCommitteeRule = () =>
  useSettingsMutation(({ methodCode, ...body }) =>
    apiFetch(`${BASE}/committee-rules/${methodCode ?? 'DEFAULT'}`, { method: 'PUT', body }),
  );

export const useDeleteCommitteeRule = () =>
  useSettingsMutation((methodCode) =>
    apiFetch(`${BASE}/committee-rules/${methodCode}`, { method: 'DELETE' }),
  );

export function useProcurementCheck(params) {
  const search = params
    ? new URLSearchParams(
        Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== ''),
      ).toString()
    : '';
  return useQuery({
    queryKey: [...KEY, 'check', search],
    queryFn: ({ signal }) => apiFetch(`${BASE}/check?${search}`, { signal }),
    enabled: Boolean(params),
  });
}

export const useSaveTemplate = () =>
  useSettingsMutation(({ code, ...body }) =>
    apiFetch(`${BASE}/evaluation-templates/${encodeURIComponent(code)}`, { method: 'PUT', body }),
  );

export const useDeleteTemplate = () =>
  useSettingsMutation((code) => apiFetch(`${BASE}/evaluation-templates/${encodeURIComponent(code)}`, { method: 'DELETE' }));
