import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload } from './client.js';

const BASE = '/api/v1/contracts';

export const contractKeys = {
  all: ['contracts'],
  list: (filter) => ['contracts', 'list', filter ?? {}],
  detail: (id) => ['contracts', id],
  history: (id) => ['contracts', id, 'history'],
  performance: ['contracts', 'supplier-performance'],
};

/** filter: { projectId } or { tenderId } (optional). */
export function useContracts(filter) {
  const qs = filter ? new URLSearchParams(Object.entries(filter).filter(([, v]) => v)).toString() : '';
  return useQuery({
    queryKey: contractKeys.list(filter),
    queryFn: ({ signal }) => apiFetch(`${BASE}${qs ? `?${qs}` : ''}`, { signal }),
  });
}

export function useContract(id) {
  return useQuery({
    queryKey: contractKeys.detail(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}`, { signal }),
    enabled: Boolean(id),
    refetchInterval: 60_000,
  });
}

export function useContractHistory(id) {
  return useQuery({
    queryKey: contractKeys.history(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}/history`, { signal }),
    enabled: Boolean(id),
  });
}

export function useSupplierPerformance() {
  return useQuery({
    queryKey: contractKeys.performance,
    queryFn: ({ signal }) => apiFetch(`${BASE}/supplier-performance`, { signal }),
    staleTime: 5 * 60_000,
  });
}

function useContractMutation(fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (detail) => {
      if (detail?.id) {
        qc.setQueryData(contractKeys.detail(detail.id), detail);
        qc.invalidateQueries({ queryKey: contractKeys.history(detail.id) });
      }
      qc.invalidateQueries({ queryKey: ['contracts', 'list'] });
    },
  });
}

const form = (fields, file) => {
  const f = new FormData();
  Object.entries(fields).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '') f.append(k, v);
  });
  if (file) f.append('file', file);
  return f;
};

export const useCreateContract = () => useContractMutation((body) => apiFetch(BASE, { method: 'POST', body }));

export const useEditContract = (id) =>
  useContractMutation((body) => apiFetch(`${BASE}/${id}`, { method: 'PUT', body }));

export const useSaveSchedule = (id) =>
  useContractMutation((milestones) => apiFetch(`${BASE}/${id}/milestones`, { method: 'PUT', body: { milestones } }));

/** action: review | cancel | sign | terminate | complete | retention-release | variations */
export const useContractAction = (id) =>
  useContractMutation(({ action, body }) => apiFetch(`${BASE}/${id}/${action}`, { method: 'POST', body: body ?? {} }));

/** fields: { kind, form, amountMinor, issuer, reference, expiryDate, receivedOn }, file optional */
export const useAddSecurity = (id) =>
  useContractMutation(({ file, ...fields }) => apiUpload(`${BASE}/${id}/securities`, form(fields, file)));

/** action: extend | close */
export const useSecurityAction = (id) =>
  useContractMutation(({ securityId, action, body }) =>
    apiFetch(`${BASE}/${id}/securities/${securityId}/${action}`, { method: 'POST', body }),
  );

export const useUploadContractDocument = (id) =>
  useContractMutation(({ kind, refId, file }) =>
    apiUpload(`${BASE}/${id}/documents?${new URLSearchParams({ kind, ...(refId ? { refId } : {}) })}`, form({}, file)),
  );

export const useAcceptMilestone = (id) =>
  useContractMutation(({ milestoneId, acceptedOn, note, file }) =>
    apiUpload(`${BASE}/${id}/milestones/${milestoneId}/accept`, form({ acceptedOn, note }, file)),
  );

/** fields: { supplierRef, invoiceDate, milestoneId, grossMinor }, file optional */
export const useRecordInvoice = (id) =>
  useContractMutation(({ file, ...fields }) => apiUpload(`${BASE}/${id}/invoices`, form(fields, file)));

/** action: certify | approve | pay | reject */
export const useInvoiceAction = (id) =>
  useContractMutation(({ invoiceId, action, body }) =>
    apiFetch(`${BASE}/${id}/invoices/${invoiceId}/${action}`, { method: 'POST', body: body ?? {} }),
  );

export const useDecideVariation = (id) =>
  useContractMutation(({ variationId, approve, note }) =>
    apiFetch(`${BASE}/${id}/variations/${variationId}/decision`, { method: 'POST', body: { approve, note } }),
  );

export const contractDocumentUrl = (id, docId) => `${BASE}/${id}/documents/${docId}`;
