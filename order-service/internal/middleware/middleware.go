package middleware

import (
	"net/http"

	"github.com/go-chi/chi/v5/middleware"
)

func Setup(mux http.Handler) http.Handler {
	return middleware.RequestID(
		middleware.Logger(
			middleware.Recoverer(
				contentTypeJSON(mux),
			),
		),
	)
}

func contentTypeJSON(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		next.ServeHTTP(w, r)
	})
}
