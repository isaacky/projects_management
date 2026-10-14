import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload } from './client.js';

const BASE = '/api/v1/suppliers';
const KEY = ['suppliers'];

export function useSuppliers() {
  return useQuery({ queryKey: KEY, queryFn: ({ signal }) => apiFetch(BASE, { signal }) });
}

export function useSaveSupplier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }) =>
      id ? apiFetch(`${BASE}/${id}`, { method: 'PUT', body }) : apiFetch(BASE, { method: 'POST', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

/** apply=false previews; apply=true saves. */
export function useImportSuppliers() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, apply }) => {
      const form = new FormData();
      form.append('file', file);
      return apiUpload(`${BASE}/import?apply=${apply ? 'true' : 'false'}`, form);
    },
    onSuccess: (result) => {
      if (result?.applied) qc.invalidateQueries({ queryKey: KEY });
    },
  });
}
