package app

import (
	"github.com/Wing900/TextbookGo/internal/auth"
	"github.com/Wing900/TextbookGo/internal/download"
)

func (s *Service) HomeData() (HomeData, error) {
	history, err := download.LoadHistory(s.OutputDir)
	if err != nil {
		return HomeData{}, err
	}

	token, err := auth.GetToken()
	return HomeData{
		OutputDir:    s.OutputDir,
		LoggedIn:     err == nil && token != "",
		HistoryCount: len(history.Books),
	}, nil
}
