import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '../models/api-error.model';
import { NotificationService } from '../services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  // Las peticiones del propio loader de traducciones (TranslateHttpLoader) no deben pasar por
  // aquí: inyectar TranslateService mientras el propio TranslateService está cargando sus
  // traducciones vía HttpClient provoca una dependencia circular (NG0200).
  if (req.url.includes('/i18n/')) {
    return next(req);
  }

  const notificationService = inject(NotificationService);
  const translateService = inject(TranslateService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const apiError = error.error as ApiError | undefined;
        const message = apiError?.message || translateService.instant('common.unexpectedError');
        notificationService.error(message);
      }
      return throwError(() => error);
    }),
  );
};
