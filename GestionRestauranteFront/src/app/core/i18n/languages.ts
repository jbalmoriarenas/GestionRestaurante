export interface LanguageOption {
  code: string;
  label: string;
}

export const AVAILABLE_LANGUAGES: LanguageOption[] = [
  { code: 'es', label: 'Español' },
  { code: 'en', label: 'English' },
];

const STORAGE_KEY = 'gestion-restaurante.lang';

export function readStoredLanguage(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && AVAILABLE_LANGUAGES.some((lang) => lang.code === stored)) {
      return stored;
    }
  } catch {
    // localStorage no disponible: se usa el idioma por defecto
  }
  return AVAILABLE_LANGUAGES[0].code;
}

export function storeLanguage(code: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, code);
  } catch {
    // localStorage no disponible: el idioma no persiste entre sesiones
  }
}
