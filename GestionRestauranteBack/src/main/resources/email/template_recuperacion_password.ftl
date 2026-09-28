<html>
	<body style="font-family: Arial, sans-serif; font-size: 15px; color: #222; margin: 0; padding: 24px;">

		<p>Hola ${(nombre)!},</p>

		<p>Hemos recibido una solicitud para restablecer tu contraseña en GestionRestaurante.</p>

		<p style="text-align: center; margin: 32px 0;">
			<a href="${enlace}" style="background-color: #2f5d34; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 4px; display: inline-block;">
				Restablecer mi contraseña
			</a>
		</p>

		<p>Si el botón no funciona, copia y pega este enlace en tu navegador:</p>
		<p><a href="${enlace}">${enlace}</a></p>

		<p>Este enlace caduca en ${expiracionMinutos?c} minutos y solo se puede usar una vez.</p>

		<p>Si no has solicitado este cambio, puedes ignorar este correo: tu contraseña no se modificará.</p>

		<p>Un saludo.</p>

	</body>
</html>
