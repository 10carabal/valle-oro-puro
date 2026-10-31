import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { mensajeApi } from '../../core/models/api-error';
import { AuthService } from '../../core/services/auth.service';

/** Registro con formulario reactivo: 409 → "Ese email ya está registrado"; éxito → /login. */
@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './registro.html',
  styleUrl: './registro.scss',
})
export class Registro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(6)],
    }),
  });

  readonly error = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly registrado = signal(false);

  enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.auth.registro(this.formulario.getRawValue()).subscribe({
      next: () => {
        this.registrado.set(true);
        this.enviando.set(false);
        this.router.navigate(['/login']);
      },
      error: (err: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(
          err.status === 409
            ? 'Ese email ya está registrado.'
            : mensajeApi(err),
        );
      },
    });
  }

  mostrarError(control: 'nombre' | 'email' | 'password', codigo: string): boolean {
    const campo = this.formulario.controls[control];
    return campo.touched && campo.hasError(codigo);
  }
}
