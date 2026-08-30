import { writable } from 'svelte/store';

export interface ToastItem {
  id: number;
  message: string;
  type: 'info' | 'success' | 'error';
}

export const toasts = writable<ToastItem[]>([]);

let toastId = 0;

export const toast = {
  show(message: string, type: 'info' | 'success' | 'error' = 'info', duration = 3200) {
    const id = ++toastId;
    toasts.update(all => [...all, { id, message, type }]);

    setTimeout(() => {
      toasts.update(all => all.filter(t => t.id !== id));
    }, duration);
  },
  success(msg: string) { toast.show(msg, 'success'); },
  error(msg: string) { toast.show(msg, 'error'); },
  info(msg: string) { toast.show(msg, 'info'); }
};
